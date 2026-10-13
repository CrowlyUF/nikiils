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

O painel segue o jogador e aparece ao lado direito dele, visível através de paredes.
Se o jogador estiver fora do alcance de renderização, um resumo aparece no canto superior esquerdo.

```
luis50 [Mod]
PING: 42ms
CPS: 9 (máx 12)
Bans: Nenhum
Mute: Não
! SafeWalk x2 (ponte sem agachar)
```

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

Cada alerta mostra quantas vezes disparou e fica visível por 30 s após o último disparo.

## Compilar

Requer JDK 17+ (compila para Java 8):

```
./gradlew build
```

O jar fica em `build/libs/MushInfo-1.0.0.jar`.
