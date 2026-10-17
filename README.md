# MushInfo — mod Forge 1.8.9

Ferramenta de moderação para o MushMC: uma ClickGUI com todos os jogadores, informações da API do
Mush (bans, mute, rank, clã, logins), detecção de vários hacks vistos de fora e o log de chat de cada
jogador.

## Instalação

1. Instale o Forge `1.8.9-11.15.1.2318` (ou mais novo para 1.8.9).
2. Coloque `dist/MushInfo-1.0.0.jar` na pasta `.minecraft/mods`.

## Uso

- **RShift** abre a ClickGUI (dá para trocar a tecla em Controles → MushInfo).
- `.info <nick>` no chat abre a GUI direto no jogador. Aceita pedaço do nick (`.info luiz` acha
  `luizgatinho771`) e **TAB** completa o nick.
- `.info clear` remove todos os cards; `.info blur` liga/desliga o blur.
- Clicar num aviso de alerta no chat abre a GUI no jogador.

### A GUI

- **Lista de jogadores** (esquerda): todos do TAB e do mundo, com a cabeça da skin, se a conta é
  **Original** ou **Pirata**, quantos alertas tem (vermelho = alerta nos últimos 30 s) e um "C" se o
  card está ligado. Quem tem card ou alerta aparece primeiro. Busca no topo (ou é só começar a
  digitar), setas ↑/↓ trocam o selecionado.
- **Detalhes** (direita): ping, CPS, reach, mira, bans, mute, primeiro/último login, amigos, rank, clã,
  todos os alertas com contagem e motivo, histórico dos últimos alertas e o **chat** do jogador (as
  últimas 100 mensagens vistas).
- As duas colunas rolam com a **roda do mouse** ou **segurando e arrastando** (ou pela barrinha).
- **Card info**: liga o card que segue o jogador e fica ao lado dele (ping, CPS, reach, mira, bans,
  mute e alertas ativos). O card some sozinho quando o jogador sai do servidor.
- **Copiar nick**, e no topo: **Blur**, **Alertas no chat** e **Experimentais** (avisar também os
  checks mais sujeitos a falso positivo). As opções ficam salvas em `config/mushinfo.properties`.

**Original/Pirata** vem da API do Mush (`type: premium` ou `cracked`). Enquanto a API não respondeu,
usa a versão do UUID (contas piratas têm UUID versão 3).

**Fonte:** a GUI e o card usam a **Comic Sans MS** do sistema (vem com o Windows). Onde ela não
existir, usa a **Comic Neue**, uma fonte parecida e livre que vai dentro do mod
(licença SIL OFL, em `assets/mushinfo/fonts/OFL-ComicNeue.txt`).

## Checks

Tudo é calculado no client com o que o servidor envia (posições, rotações, animações, blocos), então
lag pode gerar falso positivo. Use como indício para assistir o jogador, não como prova. Checks
marcados com *(exp.)* são experimentais e só avisam no chat com a opção **Experimentais** ligada
(aparecem na GUI sempre).

| Check | Como detecta |
|---|---|
| **AutoClick** *(exp.)* | O 1.8 só repassa ~7 swings/s de outro jogador, então não dá para ver 15–20 CPS. Detecta clique **no limite 30 s seguidos sem pausa** (ignora quem está minerando ou com vara de pesca). No próprio jogador: CPS ≥ 20 ou constante demais. |
| **AutoBlock** | Swing com a espada defendendo por 15 ticks seguidos (o client vanilla ignora o ataque enquanto o item está em uso). |
| **AimAssist** *(exp.)* | Em combate: erro médio da mira < 3° com o alvo andando de lado; giros de 60°+ que param em cima do alvo; ou só o yaw mexendo 25+ ticks com o pitch travado. |
| **Reach** | Distância do olho até a hitbox da vítima em cada hit, usando a posição **mais favorável** a ele dentro do atraso do ping. Acima de 3.25 repetidamente. |
| **Backtrack** *(exp.)* | O hit só alcança uma posição antiga da vítima, mais antiga do que o ping dele explica. |
| **Blink/FakeLag** | Congela (servidor para de mandar a posição) e depois "teleporta" o caminho, enquanto os outros continuam se mexendo e com ping baixo. |
| **Velocity** | Depois de um hit corpo a corpo quase não sai do lugar (sem parede atrás) em 4+ dos últimos hits. |
| **Speed** | Média acima de 0.40 bloco/tick por 1 s, sem partículas de poção, sem gelo/slime e sem ter tomado hit. |
| **NoFall** | Caiu 5+ blocos sem tomar dano (só em mundos onde já se viu alguém tomar dano de queda; ignora água, teia, escada, slime, feno e bota com Peso-Pena). |
| **NoSlow** | Anda rápido usando item, ou continua com sprint usando item (o vanilla corta o sprint). |
| **SafeWalk** | Parado na quina com metade do corpo para fora, sem agachar, sobre um vão. |
| **Scaffold** | Bloco embaixo olhando para frente; 11+ blocos/s; ponte rápida sem agachar; Tower rápido; ponte de **sprint** no plano olhando para baixo sobre o vazio. |
| **Eagle/LegitScaffold** *(exp.)* | Agacha exatamente no tick do clique 4 vezes seguidas fazendo ponte. |
| **FastMine** *(exp.)* | Quebra o bloco com a rachadura em 70–80% (o servidor aceita a partir de 70%; legit chega a 90%+). |
| **Nuker** | Quebra um bloco sem nenhuma face exposta (coberto por blocos inteiros, vidro incluso). |
| **BedNuker** | Quebra uma cama coberta (BedWars). |
| **Protocol** | Pitch impossível (> 90°). |

## Compilar

Requer JDK 17+ (compila para Java 8):

```
./gradlew build
```

O jar fica em `build/libs/MushInfo-1.0.0.jar`.
