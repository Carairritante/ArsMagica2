# Instruções persistentes de publicação deste projeto

Preferência do usuário (2026-11-17): pequenas correções não exigem renomear o
mod nem incrementar o CR. Preservar CR44 nesta correção da Bússola Astral;
distinguir os builds pelo checksum e pelo registro das alterações.

Lição de integração (2026-11-17): verificar o ciclo de preenchimento dos campos
nos JARs, além de suas assinaturas. ChromatiCraft V33 pode manter SUCCESS com
generatedLocation nula, e pylons usam cache separado das ruínas. Não confundir
campo vazio com ausência da estrutura, nem catálogo listado com localização
validada. Exibir a precisão disponível; registrar claramente quando houve só
compilação e o teste em jogo ficou para o usuário.

Identidade visual da HUD (2026-11-17): a Bússola Astral usa mostrador circular,
rosa dos ventos, cardeais e agulha de alvo próprios. Preservar posição, escala,
transparência e os controles existentes quando o pedido for apenas visual.

Estas regras registram o fluxo solicitado pelo usuário e por seu colaborador
Akashiic. Consulte-as antes de preparar commits, publicar atualizações ou alterar
os repositórios deste projeto. Novas instruções explícitas do usuário prevalecem.

## Repositórios e colaboração

- Usar forks reais dos projetos originais, preservando a relação de fork, o
  histórico, as licenças e os créditos originais.
- Concentrar nossas alterações na branch `correcoes` dos dois forks:
  - AM2.5: https://github.com/Carairritante/ArsMagica2/tree/correcoes
    Origem: DrParadox7/ArsMagica2.
  - Playground: https://github.com/Carairritante/am2playground/tree/correcoes
    Origem: AirBurn0/am2playground.
- Não substituir esses forks por repositórios independentes sem instrução
  explícita do usuário.
- O colaborador pode criar seu próprio fork ou branch e enviar pull requests
  com destino à branch `correcoes` do fork correspondente.
- Não enviar mensagens, convites ou alterar permissões de colaboradores por
  iniciativa própria. Consultar o acesso atual se isso fizer parte do pedido.

## Commits e cobertura das atualizações

- Ao publicar, conferir todas as alterações concluídas: código, patches,
  auxiliares, recursos, traduções, testes, documentação e arquivos de CI.
- Comitar as atualizações em ordem cronológica, preservando os commits existentes.
  Acrescentar novos commits ao final, com mensagens que expliquem a mudança.
- Não reordenar, condensar, apagar ou reescrever o histórico e não fazer force-push
  sem pedido explícito do usuário.
- Não incluir mudanças canceladas, experimentos abandonados, ferramentas
  temporárias, dependências, credenciais, logs privados ou saves do jogador.
- Preservar as correções já aprovadas e verificar que os arquivos publicados
  correspondem às alterações efetivamente preparadas e validadas.
- Confirmar o commit remoto e informar o que foi publicado e eventuais pendências.

## GitHub Actions e artefatos

- Manter CI automático nos dois repositórios, em pushes e pull requests.
- Disponibilizar os artefatos na própria execução do Actions.
- Publicar o JAR diretamente e o checksum SHA-256 em arquivo separado
  (`SHA256SUMS`), sem empacotar os dois em um ZIP externo.
- Preservar o upload de arquivos sem arquivamento (`archive: false`) e a retenção
  de 14 dias enquanto não houver orientação diferente do usuário.
- Não criar releases automáticas como substituto dos artefatos da execução.
- O Actions do AM2.5 compila o mod. O Actions público do Playground compila as
  ferramentas de manutenção, não um JAR jogável completo do addon.
- Um pacote de recursos em ZIP é um formato próprio do Minecraft, não o ZIP
  externo de artefatos que foi recusado. Não confundir essas duas situações.
- Diferenciar build local validado, CI em andamento e CI concluído com sucesso.
  Nunca afirmar que um CI passou sem conferir o resultado correspondente.

## Playground, autoria e arquivos privados

- Manter o JAR completo modificado do Playground no arquivo privado existente.
- No fork público do Playground, publicar os recursos, traduções e ferramentas
  de manutenção combinados com o usuário. Não publicar fontes decompilados,
  classes originais modificadas nem o JAR completo do addon.
- Manter o reconhecimento da participação substancial do OpenAI Codex na
  investigação, programação, testes e documentação, sem remover a autoria original.
- As combinações secretas das descobertas Silver não devem ser reveladas nos
  textos do Compendium. Documentar os requisitos extras já autorizados.

## Instalação e testes

- Validar em cópia descartável do `forbidden-series`, sem alterar a instância
  original sem pedido explícito. O `teste-ars` pode receber os JARs validados
  conforme o fluxo de instalação já autorizado, mas não exige novo teste.
- Antes de trocar JARs na instância de teste, confirmar que ela está fechada,
  criar backup e preservar os outros mods, configurações e saves.
- Para uma publicação, executar os checks adequados à mudança e registrar
  limites relevantes da validação. Não confundir testes descartáveis com uma
  sessão de jogo do usuário.

## Independência entre AM2 e Playground

- O AM2.5 deve funcionar sozinho e com o Playground original. Recursos
  específicos do Playground, incluindo contador cinza e política de compras
  Silver, pertencem ao Playground e só se aplicam quando ele está carregado.
- O Playground modificado não deve exigir APIs exclusivas do nosso fork do
  AM2.5. Aplicar seus hooks no próprio addon, detectar capacidades da versão
  base e manter compatibilidade com as versões originais efetivamente testadas.
- Correções de bugs nativos, como a leitura de todos os saldos que o próprio
  protocolo do AM2 transmite, continuam no AM2.5. APIs genéricas opcionais podem
  ser preservadas por compatibilidade, sem dependências obrigatórias do addon.
- Não remover silenciosamente requisitos de progressão para contornar diferenças
  entre versões. Documentar recursos ausentes e a solução de compatibilidade.
- Decisão do usuário: as sete descobertas avançadas exigem AM2.5. No AM2 original
  de Mithion, manter o restante do Playground compatível e informar a limitação;
  não substituir World Fusion por nível 70 nem recriar esse ritual no original.
- Preferência atual do usuário (2026-11-02): testar somente a nossa versão do
  AM2.5 em cópia descartável do `forbidden-series`. Não é necessário repetir
  testes no `teste-ars`, com AM2 2.0/original ou com a versão do TCLProject.
  Preservar a separação entre os projetos e registrar versões, hashes e limites
  dos testes, sem afirmar compatibilidade não verificada.
- Ao investigar arquivos de colaboradores, comparar os hashes com os artefatos
  reais do CI. Distinguir JAR jogável do arquivo de ferramentas do fork público.

## Integração e correções do Tornado (19/11/2026)

- Antes de recompilar, integrar a fonte canônica mais recente com todas as alterações locais dos agentes; CR44 sozinho não identifica o conteúdo. A base de trabalho integrada desta etapa está em tornado-fixes-cr44-20261119/worktree. Não reconstruir uma árvore antiga e substituir as correções mais recentes.
- Formas consomem etapas: o componente recebe a etapa em execução como índice zero. Tier, elemento e custo devem usar esse mesmo escopo.
- Preservar subclasses elementais de DamageSource, pois guardiões verificam instanceof. Manter construtores existentes compatíveis ao ajustar fontes locais.
- Dedicado: registrar o conjurador simulado no mundo e carregar os chunks; usar capacidade de burnout coerente com o nível. Distinguir erro de fixture, recusa de mana e bug real.
- Autorizar antes de efeitos colaterais e remoção; eventos de spawn podem cancelar ou matar a entidade. Só remover o bloco após confirmar aceitação e vida.

## Círculos verticais aprovados (20/11/2026)

- Substituir os desenhos antigos ao redor do conjurador pelos 16 modelos Mahou aprovados, preservando atlas e runas por variante. Não renderizar o manequim de referência dos modelos.
- Afinidades comuns usam somente as camadas 1–2; camada colossal 3 exclusiva dos SuperTiers. Manter centro aberto e evitar discos/pilares sobre a visão em primeira pessoa.
- Confirmação expressa do usuário: os efeitos atmosféricos dos SuperTiers continuam ativos. A substituição dos círculos não autoriza remover tempestades, céu elemental ou efeitos de queda.
- Preparar geometria uma vez, limitar partículas por tick e preservar captura corporal anterior ao freecam. Não alterar regras, custo ou dano das magias por causa desta apresentação.

## Progressão e páginas do Compêndio (20/11/2026)

- Tornado: descoberta exige nível mágico 90; preservar os outros requisitos e o conhecimento já adquirido. Alterar a validação autoritativa, avisos do Oculus e traduções em conjunto; testar a fronteira 89/90 e os demais requisitos independentemente.
- Guias extensos devem usar as quebras nativas `!p`, `!l`, `!d` e as cores `#1`/`#0`. Conferir com a fonte real do jogo: 140px de largura, 15 linhas na abertura e 17 nas demais folhas. Evitar tabelas largas.
- Conferir a lista de modificadores também com os addons presentes. No Tornado, distribuir os ícones em linhas de até sete para mantê-los na folha direita.


## Compatibilidade do laço de componentes (21/11/2026)

- O transformer do Playground 0.12.1 injeta código em `SpellHelper.applyStageToEntity` usando o slot local14 para o componente. Acrescentar variáveis locais antes do foreach desloca o slot e gera VerifyError no pack, mesmo com compilação e testes de base aprovados.
- Preservar o layout dos locais desse método; usar auxiliares sem nova variável local anterior ao laço e validar com o JAR real do Playground no servidor. Não renomear/remover o método, nem desativar os hooks de Silver para contornar o conflito.

## Modelos substitutos e encolhimento (21/11/2026)

- Nome/caixa menores com corpo grande podem indicar que o mod de modelos ignora o renderizador vanilla; não reescrever a física/câmera por esse sintoma visual. Inspecionar o caminho real de desenho e suas matrizes no JAR atual.
- Futura3D ThirdPersonRenderer.placement usa jogador local0 e matriz local10 na assinatura verificada. Aplicar escala à base, preservando a translação, depois da construção nativa da matriz, com guarda contra dupla transformação. Validar o JAR atual e o anterior quando ambos estiverem no pack.
- Registrar adaptadores opcionais no AM2.5, sem imports obrigatórios nem alterações físicas nos JARs de outros mods. Não confundir validade ASM com comprovação visual no jogo.

- Validação visual de modelos: um draw chamado em RenderTickEvent.END pode aprovar matrizes sem aparecer na captura do framebuffer, mesmo após bind manual. Preferir a GUI nativa do jogo ou a renderização normal no mundo antes de iniciar o pack completo. GuiInventory redireciona o criativo para outra tela; preparar survival somente na fixture. Não apresentar capturas sem o objeto como confirmação visual.

- Prova visual: chamadas de desenho/matrizes corretas não garantem que a captura inclua o desenho. Para comparações, preferir a tela nativa do jogo. Overlays manuais em RenderTickEvent.END podem ficar fora da imagem capturada; não anunciar confirmação visual sem abrir a imagem e ver o modelo.
- GuiInventory redireciona automaticamente jogadores criativos ao inventário criativo. Preparar o modo survival somente na fixture antes de abrir a GUI nativa, sem alterar o modo de jogo ou configurações da instância do usuário.

## Sobrecarga, persistência e efeitos repetidos (21/11/2026)

- Setters usados por NBT/sincronização não devem causar dano, morte ou punição. setCurrentFatigue deve apenas limitar/atualizar valores; a sobrecarga nasce no gasto real addBurnout, no servidor e para jogador vivo em survival.
- Burnout excedente usa Silêncio600ticks e não punições letais. Pulsos não podem renovar Silêncio continuamente: exigir recuperação<=80% e intervalo600ticks. Não encurtar/prolongar Silêncio preexistente; mana e recuperação mantidas. Decisão posterior do usuário: permitir que Mana Shield resista ao Silêncio pelas regras normais de status.
- Ao remover código de manaBurnoutTick, validar os locais que o transformer real do Playground assume, além de compilar. Restauração de NBT cheia deve ser testada sem punir quem reaparece/entra.

- Preferência explícita posterior (21/11/2026): Mana Shield DEVE poder bloquear o Silêncio de burnout normalmente, cobrando resistência de status existente. Remover a exceção interna experimental, manter Ward inalterado. Custos fracionados de Dig devem acumular até o máximo; limitar a máximo-1 apenas quando ESTOURAR, não antes.
