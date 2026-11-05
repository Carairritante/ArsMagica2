# Expansão da Box of Illusions na CR22: Mobs Vanilla, Dragão em Miniatura, Sons e Animações

## Visão Geral

A versão **1.6.11-LE-CR22** expande a `Box of Illusions` (`Caixa de Ilusões`) para incluir suporte completo e nativo a criaturas vanilla do Minecraft, sem introduzir dependências ou compatibilidade com mods externos, preservando o Guardião Arcano e mantendo total estabilidade em multiplayer.

---

## 1. Mobs Vanilla Suportados e Catalisadores

Ao clicar com o botão direito segurando a Caixa de Ilusões, o item procura o catalisador na hotbar (`0..8`) e, em seguida, no inventário principal:

| Criatura | Catalisador | Efeito / Visual |
| :--- | :--- | :--- |
| **Galinha** (`chicken`) | Pena (`feather`) ou Frango Cru (`chicken`) | Batimento suave de asas ao cair |
| **Vaca** (`cow`) | Couro (`leather`) ou Bife Cru (`beef`) | Visual e sons característicos |
| **Porco** (`pig`) | Cenoura (`carrot`) ou Costela Crua (`porkchop`) | Visual e sons característicos |
| **Ovelha** (`sheep`) | Lã (`wool`) ou Tesoura (`shears`) | Visual de ovelha com lã branca |
| **Lobo** (`wolf`) | Osso (`bone`) | Cauda dinâmica com base na vida; senta ao agachar (`Shift`) |
| **Jaguatirica/Gato** (`ocelot`) | Peixe Cru (`fish`) | Visual ágil e sons suaves de felino |
| **Cavalo** (`horse`) | Sela (`saddle`) ou Fardo de Feno (`hay_block`) | Porte nobre equino |
| **Morcego** (`bat`) | Pederneira (`flint`) | Asas batendo no ar e posição de repouso |
| **Lula** (`squid`) | Bolsa de Tinta (`dye:0`) | Tentáculos abrem e fecham na água |
| **Aldeão** (`villager`) | Livro (`book`) ou Trigo (`wheat`) | Visual tradicional de aldeão |
| **Golém de Ferro** (`iron_golem`) | Bloco de Ferro (`iron_block`) | Estrutura imponente e passos pesados |
| **Boneco de Neve** (`snowman`) | Bola de Neve (`snowball`) ou Abóbora (`pumpkin`) | Visual de boneco de neve |
| **Zumbi** (`zombie`) | Carne Podre (`rotten_flesh`) | Braços estendidos |
| **Esqueleto** (`skeleton`) | Flecha (`arrow`) | Bípede com item na mão nativo |
| **Aranha** (`spider`) | Teia (`web`) ou Olho de Aranha (`spider_eye`) | Olhos vermelhos e passos múltiplos |
| **Creeper** (`creeper`) | Pólvora (`gunpowder`) | Textura verde camuflada |
| **Bruxa** (`witch`) | Frasco de Vidro (`glass_bottle`) ou Poção | Chapéu pontudo e sons peculiares |
| **Enderman** (`enderman`) | Pérola do End (`ender_pearl`) | Corpo esguio e olhos púrpuras |
| **Blaze** (`blaze`) | Vara de Blaze (`blaze_rod`) ou Pó de Blaze (`blaze_powder`) | Varas de fogo orbitando ativamente |
| **Slime** (`slime`) | Bola de Slime (`slime_ball`) | Salto e compressão dinâmica |
| **Cubo de Magma** (`magma_cube`) | Creme de Magma (`magma_cream`) | Segmentos expansíveis ao pular |
| **Ghast** (`ghast`) | Lágrima de Ghast (`ghast_tear`) | Criatura flutuante do Nether |
| **Dragão do End** (`ender_dragon`) | **Ovo de Dragão** (`Blocks.dragon_egg`) | **Miniatura (escala 0.18x)** com asas batendo |
| **Wither** (`wither`) | Estrela do Nether (`nether_star`) | Miniatura com três cabeças |
| **Guardião Arcano** (`ender`) | Olho do End (`ender_eye`) | Mantido idêntico ao clássico do Ars Magica 2 |
| **Maid Skins** (`maid0`..`maid7`) | Padrão sem catalisador específico | Skins humanoides estilizadas preservadas |

---

## 2. Dragão do End em Miniatura (`ender_dragon`)

- Ativado consumindo visualmente o **Ovo de Dragão** (`Blocks.dragon_egg`) como catalisador.
- **Renderização e Escala**: O Dragão do End Vanilla nativo possui dimensões de chefe colossal. Na Caixa de Ilusões, ele é transladado e reduzido com precisão matemática (`GL11.glScalef(0.18F, 0.18F, 0.18F)` com offset vertical de `+0.35D`), encaixando-se harmoniosamente na escala do jogador humano.
- **Animação Contínua**: O Dragão bate as asas ciclicamente durante o voo e caminhada, calculando o ângulo e a fase de batimento com base nos ticks do jogador.

---

## 3. Animações e Física Autênticas dos Mobs

No Minecraft 1.7.10, instanciar uma nova entidade mob a cada frame de renderização zera variáveis internas como rotação de asas, compressão de pulo e ciclo de caminhada.

- **Cache Persistente (`IllusionEntityHelper`)**:
  - Cada jogador possui uma instância de entidade dedicada em cache na memória do cliente enquanto transformado.
  - O cache é invalidado e limpo automaticamente ao reverter para a forma humana ou ao desconectar do servidor.
- **Galinha e Morcego**:
  - Quando o jogador está no ar em queda (`!player.onGround && motionY < 0`), as asas batem rapidamente reproduzindo a mecânica clássica de amortecimento de queda da galinha vanilla.
- **Slime e Cubo de Magma**:
  - A compressão (`squishFactor`) reage ao impacto com o solo e ao impulso de salto.
- **Lobo**:
  - O ângulo da cauda reflete proporcionalmente a vida do jogador.
  - Ao segurar agachar (`Shift`), o lobo assume a pose sentada autêntica.
- **Blaze**:
  - As varas de fogo completam órbitas completas ao redor do torso conforme o tempo avança.

---

## 4. Sistema de Áudio Autêntico e Não-Repetitivo

Para evitar qualquer incômodo ou poluição sonora durante a jogatina e conversas com outros jogadores:
- **Intervalo Mínimo de 30 Segundos**: Sons ociosos (`idle`/`living sound`) nunca tocam em intervalos menores que 30 segundos por jogador.
- **Volume Suave e Amigável**: Sons emitidos com volume balanceado (`0.5F` a `0.7F`) e variação natural de pitch (`0.8F` a `1.2F`).
- **Sons de Dano e Morte**: Ao levar dano ou morrer, o som correspondente do mob é emitido via servidor (`world.playSoundAtEntity`), transmitido de forma síncrona para todos os jogadores próximos.
- **Efeito de Bater de Asas**: Galinhas e morcegos produzem um leve ruído de asas após quedas superiores a 2 blocos (cooldown de 8s).

---

## 5. Como Voltar a Ser Humano

- Segure **Shift + Botão Direito** com a Caixa de Ilusões na mão para dissipar a ilusão imediatamente.
- A reversão limpa o modelo local, desvincula o cache de animações e sincroniza a remoção em tempo real com o servidor e os clientes conectados.
