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

Não precisa digitar o nick inteiro: `.info luiz` acha `luizgatinho771` entre quem está no TAB ou
perto de você (se vários combinarem, escolhe o mais próximo e lista os outros no chat). Também dá
para apertar **TAB** depois de `.info ` para completar o nick; cada TAB passa para o próximo.

O painel (arredondado, fundo preto translúcido com blur do que está atrás) fica ao lado direito do
jogador, visível através de paredes. A borda fica vermelha quando há alerta. O painel só aparece
enquanto o jogador está perto o bastante para ser renderizado; quando ele sai do servidor (some do
TAB), para de ser monitorado automaticamente e uma mensagem avisa no chat.

## De onde vem cada informação

- **PING**: latência do jogador na lista do TAB (a mesma das barrinhas).
- **CPS**: cada clique em 1.8 gera uma animação de braço que o servidor repassa aos outros
  jogadores; o mod lê esses pacotes (`S0BPacketAnimation`) e conta quantos chegaram no último segundo.
- **Bans**: campos `banned` e `ban_blacklist_count` da API. A API só mostra se o jogador está banido
  *agora*; ela não expõe o histórico de bans.
- **Mute**: a API pública atualmente não expõe mute. O mod lê qualquer campo com "mute" que venha a
  aparecer; enquanto não houver, mostra "Não".
- Os dados da API são atualizados a cada 60 s.

## Detecções (heurísticas — indício, não prova)

Tudo é calculado no client com o que o servidor envia, então lag e perda de pacotes podem gerar
falsos positivos. Use como ponto de partida para observar o jogador.

- **AutoClick**: CPS ≥ 20, ou CPS ≥ 10 quase constante (desvio < 0.75) por 8 segundos seguidos.
  Humanos variam bastante segundo a segundo; autoclickers não.
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
- **Backtrack / Reach**: em cada hit do jogador (a vítima toma dano logo depois de um swing dele,
  mirando nela) o mod mede a distância do olho dele até a hitbox da vítima. O painel mostra a linha
  **Reach** (média / máximo). Se o hit passou de 3.3 blocos, o mod procura nas posições antigas da
  vítima (até 1 s atrás) qual seria alcançável: se for uma posição mais antiga do que o ping dele
  explica, conta como Backtrack; se nenhuma for alcançável, conta como Reach. Alerta com 3 em 20 s.
  A sua própria latência também distorce essas distâncias em alguns décimos, então confira com calma.

Cada alerta mostra quantas vezes disparou e fica visível por 30 s após o último disparo.

## Compilar

Requer JDK 17+ (compila para Java 8):

```
./gradlew build
```

O jar fica em `build/libs/MushInfo-1.0.0.jar`.
