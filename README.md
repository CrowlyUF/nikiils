# MushInfo — mod Forge 1.8.9

Mostra um painel ao lado de um jogador com **PING, CPS, Bans e Mute** (bans/mutes vindos da
API do MushMC: `https://mush.com.br/api/player/<nick>`), além de alertas de comportamento suspeito
para ajudar na moderação.

## Instalação

1. Instale o Forge `1.8.9-11.15.1.2318` (ou mais novo para 1.8.9).
2. Coloque `dist/MushInfo-1.0.0.jar` na pasta `.minecraft/mods`.

## Uso (no chat — nada é enviado ao servidor)

| Comando           | O que faz                                          |
|-------------------|----------------------------------------------------|
| `.info <nick>`    | Começa a mostrar o painel do jogador (repita para remover) |
| `.info`           | Mostra a ajuda e quem está sendo monitorado        |
| `.info clear`     | Remove todos                                       |
| `.info blur`      | Liga/desliga o blur do fundo (mais FPS desligado)  |

Não precisa digitar o nick inteiro: `.info luiz` acha `luizgatinho771` entre quem está no TAB ou
perto de você (se vários combinarem, escolhe o mais próximo e lista os outros no chat). Também dá
para apertar **TAB** depois de `.info ` para completar o nick; cada TAB passa para o próximo.

O painel diminui com a distância (até metade do tamanho a ~40 blocos). O painel (arredondado, fundo preto translúcido com blur do que está atrás) fica ao lado direito do
jogador, visível através de paredes. A borda fica vermelha quando há alerta. O painel só aparece
enquanto o jogador está perto o bastante para ser renderizado; quando ele sai do servidor (some do
TAB), para de ser monitorado automaticamente e uma mensagem avisa no chat.

## De onde vem cada informação

- **PING**: latência do jogador na lista do TAB (a mesma das barrinhas).
- **CPS**: cada clique gera uma animação de braço que o servidor repassa aos outros jogadores; o mod
  conta essas animações (`S0BPacketAnimation`) no último segundo. **Limite do 1.8:** o servidor só
  repassa a animação quando a anterior já passou da metade (3 de 6 ticks), então de fora nunca se vê
  mais que ~6-7 por segundo. A partir disso o painel mostra **7+** (CPS real desconhecido). No
  próprio jogador (`.info <seu nick>`) os cliques são contados direto do mouse, sem limite.
- **Bans**: campos `banned` e `ban_blacklist_count` da API. A API só mostra se o jogador está banido
  *agora*; ela não expõe o histórico de bans.
- **Mute**: a API pública atualmente não expõe mute. O mod lê qualquer campo com "mute" que venha a
  aparecer; enquanto não houver, mostra "Não".
- Os dados da API são atualizados a cada 60 s.

## Detecções (heurísticas — indício, não prova)

Tudo é calculado no client com o que o servidor envia, então lag e perda de pacotes podem gerar
falsos positivos. Use como ponto de partida para observar o jogador.

- **AutoClick**: como o CPS dos outros é cortado em ~7, não dá para ver 15 ou 20 CPS. O que dá para
  ver é ficar **no limite 30 segundos seguidos sem nenhuma pausa**, padrão de quem segura um
  autoclick (humanos param de clicar de vez em quando). No próprio jogador também vale CPS ≥ 20 ou
  CPS ≥ 10 quase constante por 8 s.
- **SafeWalk**: jogador sem agachar, no chão, com o centro do corpo para fora do bloco (sustentado só
  pela borda da hitbox) sobre um vão de 2+ blocos por 5+ ticks seguidos. Mostra "ponte sem agachar"
  quando ele está olhando para baixo (fazendo ponte).
- **NoSlow**: andando a mais de ~0.17 bloco/tick enquanto defende com a espada, come ou puxa o arco
  (o normal é ~0.04).

- **Scaffold**: blocos colocados embaixo/atrás do jogador enquanto ele olha para frente (pitch < 40°,
  3 vezes em 3 s), 11+ blocos por segundo, ponte com 8+ blocos/s correndo (> 0.26 b/t) sem agachar,
  ou Tower subindo 3+ blocos por segundo enquanto coloca bloco embaixo.
- **AimAssist**: enquanto o jogador bate em alguém que se move de lado, o mod mede o erro da mira
  (diferença entre para onde ele olha e o centro do oponente). Média abaixo de 3° em 40 amostras =
  mira "grudada". Giros de mais de 60° em 1 tick que já param em cima do alvo (3 em 10 s) também
  contam. O painel mostra a linha **Mira** com o erro médio (vermelho < 3°, amarelo < 6°). O
  servidor arredonda a rotação para ~1.4°, então esse número nunca chega a zero.

- **FakeLag / Blink**: o jogador estava andando, congela por 200 ms ou mais (o servidor para de
  mandar a posição dele) e depois "teleporta" o caminho que teria andado. Só conta se outros jogadores
  continuaram se mexendo nesse tempo (senão o lag é seu ou do servidor) e se o ping dele no TAB está
  abaixo de 200 ms (lag de verdade costuma vir com ping alto). Alerta com 3 casos em 15 s, ou 2 em
  combate.
- **Reach / Backtrack**: em cada hit do jogador (a vítima toma dano até 200 ms depois de um swing
  dele, mirando nela) o mod mede a distância do olho dele até a hitbox da vítima. Um jogador legit
  vê a vítima um pouco no passado (ping + interpolação), então a distância usada é a **menor** entre
  ele e as posições da vítima dentro dessa janela de atraso (3 a 10 ticks, conforme o ping dele): é o
  caso mais favorável a ele, para não acusar hit legit em quem está correndo ou tomando knockback.
  Hits com teleporte/lag no meio são descartados. O painel mostra a linha **Reach** (média / máximo).
  - **Reach**: acima de 3.25 blocos, pelo menos 3 vezes em 20 s, e 30%+ dos últimos hits acima de 3.2.
  - **Backtrack**: o hit não alcança a vítima dentro da janela, mas alcança uma posição bem mais
    antiga (o "bate onde o alvo estava"). 3 vezes em 20 s.

Cada alerta mostra quantas vezes disparou e fica visível por 30 s após o último disparo.

## Compilar

Requer JDK 17+ (compila para Java 8):

```
./gradlew build
```

O jar fica em `build/libs/MushInfo-1.0.0.jar`.
