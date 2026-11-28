# Fixtures de imagem

Usadas pelo `ProcessadorDeImagemVipsTest`. Cada imagem tem menos de 60 KB.

| Arquivo | Conteúdo |
|---|---|
| `retrato-exif-orientacao-6.jpg` | JPEG 1200 × 800, xadrez de 16 px, EXIF `Orientation=6` e GPS (23°33'S, 46°38'W); exibido, é 800 × 1200 |
| `paisagem.png` | PNG 1600 × 900, padrão senoidal |
| `quadrado.webp` | WebP 600 × 600 (q80), padrão senoidal |
| `texto.txt` | texto puro, para a recusa pelos bytes mágicos |

O xadrez alinhado aos blocos de 8 px do JPEG comprime bem, tem muito detalhe e some na miniatura de
32 px: é ele que torna o `borradaNaoGuardaDetalhe` significativo.

## Entradas geradas no teste

Não ficam aqui, para manter o repositório leve:

- as pretas de 3200 × 1600 e 400 × 300 saem de `vips black` no `@TempDir`;
- as gigantes (30 000 × 30 000 e 8 000 × 5 001) são PNG só com assinatura e IHDR válidos, sem dados:
  o `vipsheader` lê as dimensões do cabeçalho, e o processador recusa antes de decodificar;
- os `vips` falsos (`vips-lento`, que responde ao `--version` e faz `exec sleep 30` no resto, e
  `vips-antigo`, que responde `vips-8.14.0`) são scripts escritos no `@TempDir` com `rwx------`: o
  `maven-resources-plugin` não preserva o bit de execução.

## Como foram geradas

libvips 8.18.7 e Pillow 13.0.0 (num venv descartável):

```sh
# retrato-exif-orientacao-6.jpg: xadrez de 16 px em sRGB, depois JPEG q85 com EXIF pelo Pillow
vips xyz xy.v 1200 800
vips linear xy.v d.v 0.0625 0
vips round d.v f.v floor
vips bandmean f.v m.v
vips remainder_const m.v r.v 1
vips linear r.v c.v 510 0 --uchar
vips colourspace c.v base.png srgb
python exif.py base.png retrato-exif-orientacao-6.jpg

# paisagem.png
vips sines s.v 1600 900 --hfreq 120 --vfreq 70
vips linear s.v g.v 127.5 127.5 --uchar
vips colourspace g.v paisagem.png srgb

# quadrado.webp
vips sines s.v 600 600 --hfreq 50 --vfreq 50
vips linear s.v g.v 127.5 127.5 --uchar
vips colourspace g.v "quadrado.webp[Q=80]" srgb
```

`exif.py`:

```python
import sys
from PIL import Image
from PIL.ExifTags import IFD

origem, destino = sys.argv[1], sys.argv[2]
img = Image.open(origem).convert("RGB")
exif = Image.Exif()
exif[0x0112] = 6  # Orientation: girar 90 graus no sentido horário
gps = exif.get_ifd(IFD.GPSInfo)
gps[1] = "S"
gps[2] = (23.0, 33.0, 0.0)
gps[3] = "W"
gps[4] = (46.0, 38.0, 0.0)
img.save(destino, "JPEG", quality=85, exif=exif.tobytes())
```
