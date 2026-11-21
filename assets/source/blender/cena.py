"""Gera a cena "Antes da primeira estrela" e exporta os renders usados pela página.

Uso (headless, sem créditos):
    /Applications/Blender.app/Contents/MacOS/Blender -b -P assets/source/blender/cena.py -- <raiz-do-projeto>

Saídas em src/main/resources/static/assets/cena/:
    terraco-crepusculo.webp, terraco-noite.webp  (terraço sem props, fundo transparente)
    <prop>-crepusculo.webp, <prop>-noite.webp     (prop recortado com sombra)
    cena.json                                     (tamanhos, âncoras e lugares em px)

Câmera ortográfica fixa: o mesmo sprite serve em qualquer lugar do piso.
O céu não é renderizado; fica no CSS para permitir a transição e os meteoros.
"""

import json
import math
import os
import sys

import bpy
import numpy as np
from bpy_extras.object_utils import world_to_camera_view
from mathutils import Vector

RAIZ = sys.argv[sys.argv.index("--") + 1] if "--" in sys.argv else os.getcwd()
SAIDA = os.path.join(RAIZ, "src/main/resources/static/assets/cena")
TEMP = os.path.join(RAIZ, "target/blender")
LARGURA, ALTURA = 1800, 870
AMOSTRAS = int(os.environ.get("DUORA_AMOSTRAS", "96"))

# Lugares no piso (metros). C = área de observação, L/R = laterais.
LUGARES = {
    "C1": (-0.8, 0.2),
    "C2": (0.8, 0.2),
    "L1": (-2.45, 0.85),
    "L2": (-2.2, -1.0),
    "R1": (2.45, 0.85),
    "R2": (2.2, -1.0),
}
# Meia largura e meia profundidade do deck.
MX, MY = 3.5, 1.95
AZIMUTE = 18  # graus; revela a lateral esquerda do bloco

# Modelos gerados (GLB) substituem o construtor procedural quando existem.
# medida: ("altura" | "largura", metros) após normalizar; giro: rotação em Z (graus) para a pose.
# DUORA_GIRO_<NOME> sobrescreve o giro para testar orientações sem editar o script.
MODELOS = os.path.join(RAIZ, "assets/source/modelos")
_GIROS_PADRAO = {"telescopio": 90, "banco": 180, "manta": 0, "radio": 0, "lanterna": 0, "garrafa": 0}
_MEDIDAS = {
    "telescopio": ("altura", 1.9),
    "banco": ("altura", 0.95),
    "manta": ("largura", 1.05),
    "radio": ("altura", 0.9),
    "lanterna": ("altura", 0.95),
    "garrafa": ("altura", 0.95),
}
AJUSTES_GLB = {
    nome: {
        "medida": _MEDIDAS[nome],
        "giro": float(os.environ.get(f"DUORA_GIRO_{nome.upper()}", str(_GIROS_PADRAO[nome]))),
    }
    for nome in _MEDIDAS
}


# ---------------------------------------------------------------- utilidades


def limpar_cena():
    bpy.ops.wm.read_factory_settings(use_empty=True)


def hex_rgb(h):
    h = h.lstrip("#")
    srgb = [int(h[i : i + 2], 16) / 255 for i in (0, 2, 4)]
    return tuple(c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4 for c in srgb) + (1.0,)


_MATERIAIS = {}


def material(nome, cor, rugosidade=0.6, metal=0.0, emissao=None, forca=0.0, transmissao=0.0):
    if nome in _MATERIAIS:
        return _MATERIAIS[nome]
    m = bpy.data.materials.new(nome)
    m.use_nodes = True
    p = m.node_tree.nodes["Principled BSDF"]
    p.inputs["Base Color"].default_value = hex_rgb(cor)
    p.inputs["Roughness"].default_value = rugosidade
    p.inputs["Metallic"].default_value = metal
    if transmissao:
        p.inputs["Transmission Weight"].default_value = transmissao
    if emissao:
        p.inputs["Emission Color"].default_value = hex_rgb(emissao)
        p.inputs["Emission Strength"].default_value = forca
    _MATERIAIS[nome] = m
    return m


def material_xadrez(nome, base, faixa, faixa2):
    """Tecido xadrez para a manta: duas ondas cruzadas misturadas à cor base."""
    m = bpy.data.materials.new(nome)
    m.use_nodes = True
    nt = m.node_tree
    p = nt.nodes["Principled BSDF"]
    p.inputs["Roughness"].default_value = 0.9
    coord = nt.nodes.new("ShaderNodeTexCoord")
    ondas = []
    for eixo in ("X", "Y"):
        w = nt.nodes.new("ShaderNodeTexWave")
        w.wave_type = "BANDS"
        w.bands_direction = eixo
        w.inputs["Scale"].default_value = 3.2
        w.inputs["Distortion"].default_value = 0.0
        nt.links.new(coord.outputs["Object"], w.inputs["Vector"])
        r = nt.nodes.new("ShaderNodeValToRGB")
        r.color_ramp.interpolation = "CONSTANT"
        r.color_ramp.elements[0].position = 0.0
        r.color_ramp.elements[0].color = (0, 0, 0, 1)
        r.color_ramp.elements[1].position = 0.72
        r.color_ramp.elements[1].color = (1, 1, 1, 1)
        nt.links.new(w.outputs["Fac"], r.inputs["Fac"])
        ondas.append(r)
    mix1 = nt.nodes.new("ShaderNodeMix")
    mix1.data_type = "RGBA"
    mix1.inputs["A"].default_value = hex_rgb(base)
    mix1.inputs["B"].default_value = hex_rgb(faixa)
    nt.links.new(ondas[0].outputs["Color"], mix1.inputs["Factor"])
    mix2 = nt.nodes.new("ShaderNodeMix")
    mix2.data_type = "RGBA"
    mix2.inputs["B"].default_value = hex_rgb(faixa2)
    nt.links.new(mix1.outputs["Result"], mix2.inputs["A"])
    nt.links.new(ondas[1].outputs["Color"], mix2.inputs["Factor"])
    nt.links.new(mix2.outputs["Result"], p.inputs["Base Color"])
    return m


def _finalizar(o, nome, mat, chanfro, pai):
    o.name = nome
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    if mat:
        o.data.materials.append(mat)
    if chanfro:
        b = o.modifiers.new("Chanfro", "BEVEL")
        b.width = chanfro
        b.segments = 2
        b.limit_method = "ANGLE"
    if pai:
        o.parent = pai
    return o


def caixa(nome, tamanho, pos, mat, chanfro=0.015, rot=(0, 0, 0), pai=None):
    bpy.ops.mesh.primitive_cube_add(size=1, location=pos, rotation=rot)
    o = bpy.context.active_object
    o.scale = tamanho
    return _finalizar(o, nome, mat, chanfro, pai)


def cilindro(nome, raio, altura, pos, mat, rot=(0, 0, 0), raio2=None, lados=32, chanfro=0.0, pai=None):
    bpy.ops.mesh.primitive_cone_add(
        vertices=lados, radius1=raio, radius2=raio if raio2 is None else raio2, depth=altura, location=pos, rotation=rot
    )
    o = bpy.context.active_object
    bpy.ops.object.shade_smooth()
    return _finalizar(o, nome, mat, chanfro, pai)


def cilindro_entre(nome, a, b, raio, mat, pai=None):
    a, b = Vector(a), Vector(b)
    d = b - a
    rot = d.to_track_quat("Z", "Y").to_euler()
    return cilindro(nome, raio, d.length, (a + b) / 2, mat, rot=rot, lados=16, pai=pai)


def esfera(nome, raio, pos, mat, escala=(1, 1, 1), pai=None, subdiv=2):
    bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=subdiv, radius=raio, location=pos)
    o = bpy.context.active_object
    o.scale = escala
    bpy.ops.object.shade_smooth()
    return _finalizar(o, nome, mat, 0, pai)


def vazio(nome):
    e = bpy.data.objects.new(nome, None)
    bpy.context.scene.collection.objects.link(e)
    return e


# ---------------------------------------------------------------- terraço


def material_tijolo(nome):
    m = bpy.data.materials.new(nome)
    m.use_nodes = True
    nt = m.node_tree
    p = nt.nodes["Principled BSDF"]
    p.inputs["Roughness"].default_value = 0.9
    t = nt.nodes.new("ShaderNodeTexBrick")
    t.inputs["Color1"].default_value = hex_rgb("#3A5067")
    t.inputs["Color2"].default_value = hex_rgb("#33475C")
    t.inputs["Mortar"].default_value = hex_rgb("#22313F")
    t.inputs["Scale"].default_value = 2.2
    t.inputs["Mortar Size"].default_value = 0.012
    coord = nt.nodes.new("ShaderNodeTexCoord")
    mapa = nt.nodes.new("ShaderNodeMapping")
    mapa.inputs["Rotation"].default_value = (math.radians(90), 0, 0)
    nt.links.new(coord.outputs["Object"], mapa.inputs["Vector"])
    nt.links.new(mapa.outputs["Vector"], t.inputs["Vector"])
    nt.links.new(t.outputs["Color"], p.inputs["Base Color"])
    return m


def construir_terraco(momento):
    raiz = vazio("Terraco")
    fachada = material_tijolo("Fachada")
    cornija = material("Cornija", "#7D93A6", 0.6)
    base = material("BaseEscura", "#18232F", 0.9)
    madeiras = [material(f"Deck{i}", c, 0.7) for i, c in enumerate(("#A08A72", "#968069", "#A9937A"))]
    ferro = material("Ferro", "#1D2733", 0.45, metal=0.6)
    corrimao = material("Corrimao", "#8E6A4C", 0.55)
    vaso = material("Vaso", "#2F5866", 0.5)
    folhas = [material(f"Folha{i}", c, 0.8) for i, c in enumerate(("#3D6B4F", "#4C7D57", "#355E47"))]
    fio = material("Fio", "#141B24", 0.6)
    noite = momento == "noite"
    lampada = material(
        "LampadaNoite" if noite else "Lampada", "#FFE2B0", 0.4, emissao="#FFC982", forca=14 if noite else 3
    )

    # Bloco do diorama: topo do prédio com fachada de tijolos, cornija e base.
    largura, profundidade = 2 * MX + 0.3, 2 * MY + 0.3
    caixa("Fachada", (largura, profundidade, 0.9), (0, 0, -0.55), fachada, 0.01, pai=raiz)
    caixa("Cornija", (largura + 0.16, profundidade + 0.16, 0.12), (0, 0, -0.1), cornija, 0.02, pai=raiz)
    caixa("Friso", (largura + 0.06, profundidade + 0.06, 0.05), (0, 0, -0.62), cornija, 0.01, pai=raiz)
    caixa("Base", (largura - 0.1, profundidade - 0.1, 0.12), (0, 0, -1.06), base, 0.01, pai=raiz)

    # Deck de tábuas ao longo de X.
    y, i = -MY, 0
    while y < MY - 0.01:
        caixa(f"Tabua{i}", (2 * MX, 0.33, 0.06), (0, y + 0.165, -0.01), madeiras[i % 3], 0.012, pai=raiz)
        y += 0.35
        i += 1

    # Guarda-corpo no fundo e nas laterais (frente aberta para a câmera).
    xf = MX + 0.05
    yf = MY + 0.05
    postes = 9
    xs = [-xf + k * (2 * xf) / (postes - 1) for k in range(postes)]
    for k, x in enumerate(xs):
        caixa(f"PosteFundo{k}", (0.07, 0.07, 0.95), (x, yf, 0.475), ferro, 0.01, pai=raiz)
    caixa("CorrimaoFundo", (2 * xf + 0.1, 0.1, 0.07), (0, yf, 0.98), corrimao, 0.015, pai=raiz)
    caixa("BarraFundo", (2 * xf, 0.03, 0.03), (0, yf, 0.45), ferro, 0, pai=raiz)
    for lado in (-1, 1):
        x = lado * xf
        for k, yy in enumerate((-MY + 0.25, 0.0, MY - 0.4)):
            caixa(f"Poste{lado}_{k}", (0.07, 0.07, 0.95), (x, yy, 0.475), ferro, 0.01, pai=raiz)
        caixa(f"Corrimao{lado}", (0.1, 2 * yf, 0.07), (x, 0.0, 0.98), corrimao, 0.015, pai=raiz)
        caixa(f"Barra{lado}", (0.03, 2 * yf, 0.03), (x, 0.0, 0.45), ferro, 0, pai=raiz)

    # Varal de lâmpadas entre os postes do fundo, com leve barriga.
    for k in range(postes - 1):
        a, b = xs[k], xs[k + 1]
        pontos = []
        for t in (0.0, 0.25, 0.5, 0.75, 1.0):
            x = a + (b - a) * t
            z = 1.12 - 0.16 * math.sin(math.pi * t)
            pontos.append((x, yf - 0.02, z))
        for q in range(len(pontos) - 1):
            cilindro_entre(f"Fio{k}_{q}", pontos[q], pontos[q + 1], 0.006, fio, pai=raiz)
        x, _, z = pontos[2]
        esfera(f"Lampada{k}", 0.045, (x, yf - 0.02, z - 0.06), lampada, (1, 1, 1.25), pai=raiz)
    for k, x in enumerate(xs):
        cilindro_entre(f"Haste{k}", (x, yf, 0.98), (x, yf, 1.14), 0.012, ferro, pai=raiz)

    # Vasos nos cantos do fundo.
    for lado in (-1, 1):
        x, yy = lado * (MX - 0.5), MY - 0.3
        cilindro(f"Vaso{lado}", 0.25, 0.46, (x, yy, 0.23), vaso, raio2=0.32, chanfro=0.01, pai=raiz)
        for j, (dx, dy, dz, r) in enumerate(
            ((0, 0, 0.7, 0.3), (0.19, 0.05, 0.6, 0.22), (-0.19, -0.05, 0.62, 0.23), (0.05, -0.1, 0.88, 0.2))
        ):
            esfera(f"Folhagem{lado}_{j}", r, (x + dx * lado, yy + dy, dz), folhas[j % 3], (1, 1, 0.85), pai=raiz)
    return raiz


# ---------------------------------------------------------------- props


def importar_glb(nome_raiz, caminho, medida, giro, espelhado=False):
    """Importa um GLB, apoia a base em z=0 no centro e normaliza altura ou largura."""
    antes = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=caminho)
    novos = [o for o in bpy.data.objects if o not in antes]
    bpy.context.view_layer.update()
    malhas = [o for o in novos if o.type == "MESH"]
    # Os GLBs gerados têm ~2 milhões de faces; o sprite tem ~400 px.
    for o in malhas:
        reducao = o.modifiers.new("Reducao", "DECIMATE")
        reducao.ratio = 0.08
    pontos = [o.matrix_world @ Vector(c) for o in malhas for c in o.bound_box]
    minimo = Vector((min(p.x for p in pontos), min(p.y for p in pontos), min(p.z for p in pontos)))
    maximo = Vector((max(p.x for p in pontos), max(p.y for p in pontos), max(p.z for p in pontos)))
    base = Vector(((minimo.x + maximo.x) / 2, (minimo.y + maximo.y) / 2, minimo.z))
    eixo, metros = medida
    dimensao = maximo.z - minimo.z if eixo == "altura" else max(maximo.x - minimo.x, maximo.y - minimo.y)
    escala = metros / dimensao

    # raiz (escala e espelho no X do mundo) → giro (pose) → pivô (base na origem) → malhas.
    raiz = vazio(nome_raiz)
    rotacao = vazio(nome_raiz + "-giro")
    pivo = vazio(nome_raiz + "-pivo")
    pivo.location = -base
    pivo.parent = rotacao
    rotacao.parent = raiz
    rotacao.rotation_euler = (0, 0, math.radians(giro))
    for o in novos:
        if o.parent is None:
            o.parent = pivo
    raiz.scale = (-escala if espelhado else escala, escala, escala)
    return raiz


def acender_vela(raiz, altura):
    """O modelo vem com a vela apagada: chama emissiva + luz interna sem sombra."""
    bpy.context.view_layer.update()
    z = altura * float(os.environ.get("DUORA_ALTURA_CHAMA", "0.42"))
    chama = material("ChamaGlb", "#FFD08A", 0.5, emissao="#FFC27A", forca=60)
    o = esfera("ChamaGlb", 0.035, (0, 0, z), chama, (1, 1, 1.7))
    o.parent = raiz
    o.matrix_parent_inverse = raiz.matrix_world.inverted()
    luz = bpy.data.lights.new("LuzLanterna", "POINT")
    luz.energy = 30
    luz.color = (1.0, 0.75, 0.45)
    luz.shadow_soft_size = 0.05
    luz.use_shadow = False
    lo = bpy.data.objects.new("LuzLanterna", luz)
    lo.location = (0, 0, z - 0.03)
    bpy.context.scene.collection.objects.link(lo)
    lo.parent = raiz
    lo.matrix_parent_inverse = raiz.matrix_world.inverted()


def glb(nome):
    caminho = os.path.join(MODELOS, f"{nome}.glb")
    return caminho if os.path.exists(caminho) else None


def prop_telescopio(espelhado):
    raiz = vazio("telescopio-esq" if espelhado else "telescopio-dir")
    madeira = material("TripeMadeira", "#8A6240", 0.55)
    tubo = material("TuboEsmalte", "#2E4A66", 0.3, metal=0.2)
    latao = material("Latao", "#C9A15A", 0.3, metal=1.0)
    topo = Vector((0, 0, 1.05))
    for k, ang in enumerate((90, 210, 330)):
        a = math.radians(ang)
        cilindro_entre(f"Perna{k}", topo, (0.48 * math.cos(a), 0.48 * math.sin(a), 0.0), 0.035, madeira, pai=raiz)
    caixa("Montagem", (0.16, 0.16, 0.12), topo, latao, 0.02, pai=raiz)
    d = Vector((1.0, 0.55, 0.85)).normalized()
    rot = d.to_track_quat("Z", "Y").to_euler()
    centro = topo + d * 0.22 + Vector((0, 0, 0.1))
    cilindro("Tubo", 0.12, 1.35, centro, tubo, rot=rot, chanfro=0.0, pai=raiz)
    cilindro("Objetiva", 0.15, 0.16, centro + d * 0.68, latao, rot=rot, pai=raiz)
    cilindro("Anel", 0.13, 0.05, centro + d * 0.1, latao, rot=rot, pai=raiz)
    cilindro("Ocular", 0.05, 0.18, centro - d * 0.74, latao, rot=rot, pai=raiz)
    cilindro("Buscador", 0.035, 0.38, centro + d * 0.2 + Vector((0, 0, 0.17)), tubo, rot=rot, pai=raiz)
    if espelhado:
        raiz.scale.x = -1
    return raiz


def prop_banco():
    raiz = vazio("banco")
    madeira = material("BancoMadeira", "#A0703F", 0.5)
    ferro = material("Ferro", "#1D2733", 0.45, metal=0.6)
    for k, yy in enumerate((-0.14, 0.0, 0.14)):
        caixa(f"Ripa{k}", (1.6, 0.12, 0.05), (0, yy, 0.47), madeira, 0.012, pai=raiz)
    # Encosto voltado para a câmera: quem senta olha para o céu.
    for k, zz in enumerate((0.68, 0.86)):
        caixa(f"Encosto{k}", (1.6, 0.05, 0.12), (0, -0.25, zz), madeira, 0.012, rot=(math.radians(-8), 0, 0), pai=raiz)
    for lado in (-1, 1):
        x = lado * 0.66
        caixa(f"Pe{lado}a", (0.06, 0.06, 0.45), (x, 0.17, 0.225), ferro, 0.01, pai=raiz)
        caixa(f"Pe{lado}b", (0.06, 0.06, 0.95), (x, -0.25, 0.475), ferro, 0.01, pai=raiz)
        caixa(f"Braco{lado}", (0.06, 0.46, 0.05), (x, -0.03, 0.62), ferro, 0.01, pai=raiz)
    return raiz


def prop_manta():
    raiz = vazio("manta")
    tecido = material_xadrez("MantaXadrez", "#7E3B45", "#D9A441", "#F1E3C8")
    almofada = material("Almofada", "#A8D5C5", 0.85)
    bpy.ops.mesh.primitive_plane_add(size=1, location=(0, 0, 0.025))
    m = bpy.context.active_object
    m.scale = (1.35, 1.0, 1)
    _finalizar(m, "Tecido", tecido, 0, raiz)
    s = m.modifiers.new("Espessura", "SOLIDIFY")
    s.thickness = 0.035
    caixa("Dobra", (1.35, 0.3, 0.05), (0, -0.4, 0.07), tecido, 0.025, pai=raiz)
    esfera("Almofada", 0.24, (0.38, 0.22, 0.12), almofada, (1.3, 0.9, 0.45), pai=raiz, subdiv=3)
    return raiz


def caixote(raiz, nome, tamanho):
    madeira = material("Caixote", "#8C6A4E", 0.7)
    w, d, h = tamanho
    caixa(nome, (w, d, h), (0, 0, h / 2), madeira, 0.02, pai=raiz)
    return h


def prop_radio(noite):
    raiz = vazio("radio")
    h = caixote(raiz, "Caixote", (0.62, 0.5, 0.42))
    corpo = material("RadioCorpo", "#E8DCC2", 0.45)
    grade = material("RadioGrade", "#3F6F6A", 0.6)
    dial = material("RadioDialNoite" if noite else "RadioDial", "#F2BD70", 0.3, emissao="#F2BD70", forca=6 if noite else 0.4)
    ferro = material("Ferro", "#1D2733", 0.45, metal=0.6)
    caixa("Corpo", (0.62, 0.28, 0.4), (0, 0, h + 0.2), corpo, 0.05, pai=raiz)
    cilindro("Alto-falante", 0.12, 0.02, (-0.13, -0.145, h + 0.2), grade, rot=(math.radians(90), 0, 0), pai=raiz)
    caixa("Dial", (0.2, 0.02, 0.09), (0.16, -0.145, h + 0.24), dial, 0.01, pai=raiz)
    cilindro("Botao", 0.035, 0.04, (0.16, -0.155, h + 0.11), ferro, rot=(math.radians(90), 0, 0), pai=raiz)
    cilindro_entre("Antena", (0.25, 0.05, h + 0.4), (0.42, 0.12, h + 0.95), 0.008, ferro, pai=raiz)
    cilindro_entre("Alca", (-0.22, 0, h + 0.4), (0.18, 0, h + 0.4), 0.02, ferro, pai=raiz)
    return raiz


def prop_lanterna(noite):
    raiz = vazio("lanterna")
    ferro = material("Ferro", "#1D2733", 0.45, metal=0.6)
    vidro = material("Vidro", "#F5F1E7", 0.08, transmissao=0.9)
    chama = material(
        "ChamaNoite" if noite else "Chama", "#FFD08A", 0.5, emissao="#FFC27A", forca=40 if noite else 4
    )
    caixa("BaseLanterna", (0.4, 0.4, 0.08), (0, 0, 0.04), ferro, 0.01, pai=raiz)
    for sx in (-1, 1):
        for sy in (-1, 1):
            caixa(f"Haste{sx}{sy}", (0.035, 0.035, 0.62), (sx * 0.17, sy * 0.17, 0.39), ferro, 0.005, pai=raiz)
    caixa("Vidro", (0.32, 0.32, 0.6), (0, 0, 0.39), vidro, 0.0, pai=raiz)
    cilindro("Vela", 0.06, 0.2, (0, 0, 0.18), material("Vela", "#F5F1E7", 0.6), pai=raiz)
    esfera("Chama", 0.045, (0, 0, 0.33), chama, (1, 1, 1.7), pai=raiz)
    cilindro("Telhado", 0.3, 0.16, (0, 0, 0.78), ferro, raio2=0.04, lados=4, rot=(0, 0, math.radians(45)), pai=raiz)
    cilindro("Argola", 0.07, 0.02, (0, 0, 0.9), ferro, rot=(math.radians(90), 0, 0), pai=raiz)
    if noite:
        luz = bpy.data.lights.new("LuzLanterna", "POINT")
        luz.energy = 25
        luz.color = (1.0, 0.75, 0.45)
        luz.shadow_soft_size = 0.05
        luz.use_shadow = False  # sem isso, o vidro projeta sombras no apanhador inteiro
        o = bpy.data.objects.new("LuzLanterna", luz)
        o.location = (0, 0, 0.34)
        bpy.context.scene.collection.objects.link(o)
        o.parent = raiz
    return raiz


def prop_garrafa():
    raiz = vazio("garrafa")
    h = 0.45
    mesa = material("Mesinha", "#8C6A4E", 0.65)
    cilindro("Tampo", 0.32, 0.05, (0, 0, h), mesa, chanfro=0.01, pai=raiz)
    for k, ang in enumerate((45, 165, 285)):
        a = math.radians(ang)
        cilindro_entre(f"PeMesa{k}", (0.2 * math.cos(a), 0.2 * math.sin(a), h), (0.27 * math.cos(a), 0.27 * math.sin(a), 0), 0.025, mesa, pai=raiz)
    corpo = material("Garrafa", "#6FA79A", 0.35, metal=0.3)
    marfim = material("Marfim", "#F5F1E7", 0.5)
    cilindro("Garrafa", 0.1, 0.5, (-0.08, 0.04, h + 0.27), corpo, chanfro=0.01, pai=raiz)
    cilindro("Tampa", 0.085, 0.1, (-0.08, 0.04, h + 0.57), marfim, chanfro=0.01, pai=raiz)
    for k, (x, y) in enumerate(((0.16, -0.08), (0.12, 0.14))):
        cilindro(f"Xicara{k}", 0.06, 0.1, (x, y, h + 0.075), marfim, raio2=0.07, pai=raiz)
    return raiz


# ---------------------------------------------------------------- luz e render


def configurar_render():
    cena = bpy.context.scene
    cena.render.engine = "CYCLES"
    prefs = bpy.context.preferences.addons["cycles"].preferences
    try:
        prefs.compute_device_type = "METAL"
        prefs.get_devices()
        for d in prefs.devices:
            d.use = True
        cena.cycles.device = "GPU"
    except (TypeError, ValueError):
        cena.cycles.device = "CPU"
    cena.cycles.samples = AMOSTRAS
    cena.cycles.use_denoising = True
    cena.render.film_transparent = True
    cena.render.resolution_x = LARGURA
    cena.render.resolution_y = ALTURA
    cena.render.resolution_percentage = 100
    cena.view_settings.view_transform = "Standard"
    cena.view_settings.look = "None"
    cena.render.image_settings.file_format = "PNG"
    cena.render.image_settings.color_mode = "RGBA"


def camera():
    cam = bpy.data.cameras.new("Camera")
    cam.type = "ORTHO"
    cam.ortho_scale = 9.0
    o = bpy.data.objects.new("Camera", cam)
    bpy.context.scene.collection.objects.link(o)
    o.rotation_euler = (math.radians(60), 0, math.radians(-AZIMUTE))
    direcao = o.rotation_euler.to_matrix() @ Vector((0, 0, -1))
    o.location = Vector((0, 0.0, 0.0)) - direcao * 30
    bpy.context.scene.camera = o
    return o


def iluminacao(momento):
    for o in [o for o in bpy.data.objects if o.type == "LIGHT" and o.name.startswith("Luz") and o.name != "LuzLanterna"]:
        bpy.data.objects.remove(o)
    mundo = bpy.data.worlds.get("Mundo") or bpy.data.worlds.new("Mundo")
    mundo.use_nodes = True
    fundo = mundo.node_tree.nodes["Background"]
    bpy.context.scene.world = mundo
    if momento == "crepusculo":
        fundo.inputs["Color"].default_value = hex_rgb("#5C7FA0")
        fundo.inputs["Strength"].default_value = 0.55
        chave = ((1.0, 0.74, 0.52), 3.2, (math.radians(55), 0, math.radians(-38)))
    else:
        fundo.inputs["Color"].default_value = hex_rgb("#163250")
        fundo.inputs["Strength"].default_value = 0.22
        chave = ((0.55, 0.68, 1.0), 0.45, (math.radians(45), 0, math.radians(30)))
    cor, forca, rot = chave
    sol = bpy.data.lights.new("LuzChave", "SUN")
    sol.color = cor
    sol.energy = forca
    sol.angle = math.radians(6)
    o = bpy.data.objects.new("LuzChave", sol)
    o.rotation_euler = rot
    bpy.context.scene.collection.objects.link(o)


def enquadrar(cam, raiz, margem=0.03, folga_topo=0.12):
    """Ajusta escala, deslocamento e altura do render para caber o terraço."""
    global ALTURA
    cena = bpy.context.scene
    bpy.context.view_layer.update()
    us, vs = [], []
    for o in objetos_de(raiz):
        if o.type != "MESH":
            continue
        for v in o.data.vertices:
            c = world_to_camera_view(cena, cam, o.matrix_world @ v.co)
            us.append(c.x)
            vs.append(c.y)
    escala = cam.data.ortho_scale
    sx = escala
    sy = escala * ALTURA / LARGURA
    u0, u1, v0, v1 = min(us), max(us), min(vs), max(vs)
    largura_u = (u1 - u0) * sx * (1 + 2 * margem)
    altura_u = (v1 - v0) * sy * (1 + 2 * margem + folga_topo)
    centro_u = ((u0 + u1) / 2 - 0.5) * sx + cam.data.shift_x * escala
    centro_v = ((v0 + v1) / 2 - 0.5) * sy + cam.data.shift_y * escala + (v1 - v0) * sy * folga_topo / 2
    cam.data.ortho_scale = largura_u
    ALTURA = int(round(LARGURA * altura_u / largura_u / 2) * 2)
    cena.render.resolution_y = ALTURA
    cam.data.shift_x = centro_u / largura_u
    cam.data.shift_y = centro_v / largura_u


def apanhador_sombra():
    bpy.ops.mesh.primitive_plane_add(size=6, location=(0, 0, 0))
    o = bpy.context.active_object
    o.name = "ApanhaSombra"
    o.is_shadow_catcher = True
    return o


def objetos_de(raiz):
    return [raiz] + list(raiz.children_recursive)


def ocultar(raiz, oculto):
    for o in objetos_de(raiz):
        o.hide_render = oculto


def renderizar(caminho):
    bpy.context.scene.render.filepath = caminho
    bpy.ops.render.render(write_still=True)


def pixel(cam, ponto):
    c = world_to_camera_view(bpy.context.scene, cam, Vector(ponto))
    return round(c.x * LARGURA, 1), round((1 - c.y) * ALTURA, 1)


def recortar(origem, destino):
    """Recorta a área com alfa e salva em WebP; devolve (x0, y0, largura, altura) em px."""
    img = bpy.data.images.load(origem)
    w, h = img.size
    px = np.array(img.pixels[:], dtype=np.float32).reshape(h, w, 4)[::-1]
    # Ignora ruído do denoiser: exige alguns pixels acima do limiar por linha/coluna.
    visivel = px[:, :, 3] > 0.04
    linhas = np.where(visivel.sum(axis=1) > 2)[0]
    colunas = np.where(visivel.sum(axis=0) > 2)[0]
    margem = 4
    y0, y1 = max(linhas[0] - margem, 0), min(linhas[-1] + margem + 1, h)
    x0, x1 = max(colunas[0] - margem, 0), min(colunas[-1] + margem + 1, w)
    recorte = px[y0:y1, x0:x1][::-1]
    nova = bpy.data.images.new(os.path.basename(destino), x1 - x0, y1 - y0, alpha=True)
    nova.pixels = recorte.ravel()
    nova.filepath_raw = destino
    nova.file_format = "WEBP"
    nova.save(quality=QUALIDADE_WEBP)
    bpy.data.images.remove(img)
    bpy.data.images.remove(nova)
    return int(x0), int(y0), int(x1 - x0), int(y1 - y0)


# Padrão único para todas as imagens da página (cena e céu): WebP qualidade 88.
QUALIDADE_WEBP = 88


def renderizar_webp(caminho, qualidade=QUALIDADE_WEBP):
    ajustes = bpy.context.scene.render.image_settings
    ajustes.file_format = "WEBP"
    ajustes.quality = qualidade
    renderizar(caminho)
    ajustes.file_format = "PNG"


# ---------------------------------------------------------------- principal


def main():
    os.makedirs(SAIDA, exist_ok=True)
    os.makedirs(TEMP, exist_ok=True)
    limpar_cena()
    configurar_render()
    cam = camera()
    sombra = apanhador_sombra()
    so = os.environ.get("DUORA_SO")
    provisorio = construir_terraco("crepusculo")
    enquadrar(cam, provisorio)
    for o in objetos_de(provisorio):
        bpy.data.objects.remove(o)  # ex.: "terraco" ou "banco" para iterar mais rápido

    dados = {
        "largura": LARGURA,
        "altura": ALTURA,
        "pxPorMetro": round(LARGURA / cam.data.ortho_scale, 2),
        "lugares": {k: dict(zip(("x", "y"), pixel(cam, (x, y, 0)))) for k, (x, y) in LUGARES.items()},
        "props": {},
    }
    ancora = pixel(cam, (0, 0, 0))

    for momento in ("crepusculo", "noite"):
        iluminacao(momento)
        terraco = construir_terraco(momento)
        if not so or so == "terraco":
            sombra.hide_render = True
            ocultar(terraco, False)
            renderizar_webp(os.path.join(SAIDA, f"terraco-{momento}.webp"))

        sombra.hide_render = False
        ocultar(terraco, True)
        noite = momento == "noite"

        def por_glb(nome, procedural, espelhado=False):
            if not glb(nome):
                return procedural()
            a = AJUSTES_GLB[nome]
            raiz = importar_glb(nome, glb(nome), a["medida"], a["giro"], espelhado)
            if nome == "lanterna" and noite:
                acender_vela(raiz, a["medida"][1])
            return raiz

        construtores = {
            "telescopio-esq": lambda: por_glb("telescopio", lambda: prop_telescopio(True), espelhado=True),
            "telescopio-dir": lambda: por_glb("telescopio", lambda: prop_telescopio(False)),
            "banco": lambda: por_glb("banco", prop_banco),
            "manta": lambda: por_glb("manta", prop_manta),
            "radio": lambda: por_glb("radio", lambda: prop_radio(noite)),
            "lanterna": lambda: por_glb("lanterna", lambda: prop_lanterna(noite)),
            "garrafa": lambda: por_glb("garrafa", prop_garrafa),
        }
        for nome, construir in construtores.items():
            if so and so != nome:
                continue
            raiz = construir()
            png = os.path.join(TEMP, f"{nome}-{momento}.png")
            renderizar(png)
            x0, y0, w, h = recortar(png, os.path.join(SAIDA, f"{nome}-{momento}.webp"))
            dados["props"].setdefault(nome, {})[momento] = {
                "largura": w,
                "altura": h,
                "ancoraX": round(ancora[0] - x0, 1),
                "ancoraY": round(ancora[1] - y0, 1),
            }
            for o in objetos_de(raiz):
                bpy.data.objects.remove(o)
        if momento == "crepusculo" or so:
            for o in objetos_de(terraco):
                bpy.data.objects.remove(o)
        else:
            ocultar(terraco, False)

    arquivo_json = os.path.join(SAIDA, "cena.json")
    if so and os.path.exists(arquivo_json):
        # Render parcial: só atualiza as medidas dos props renderizados agora.
        with open(arquivo_json, encoding="utf-8") as f:
            existente = json.load(f)
        existente["props"].update(dados["props"])
        dados = existente
    with open(arquivo_json, "w", encoding="utf-8") as f:
        json.dump(dados, f, ensure_ascii=False, indent=2)
    if not so:
        bpy.ops.wm.save_as_mainfile(filepath=os.path.join(TEMP, "cena.blend"))
    print("DUORA_OK", json.dumps(dados["lugares"]))


main()
