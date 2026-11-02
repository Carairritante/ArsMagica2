# Instruções persistentes de publicação deste projeto

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

- A instância de teste é `teste-ars`; o `forbidden-series` serve como referência.
  Não alterar o forbidden-series sem pedido explícito.
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
- Antes de publicar mudanças nessas integrações, testar AM2.5 sem Playground,
  com Playground original e com Playground corrigido, além do Playground
  corrigido com AM2 original suportado. Registrar versões e hashes; não afirmar
  compatibilidade universal com versões que não foram verificadas.
- Ao investigar arquivos de colaboradores, comparar os hashes com os artefatos
  reais do CI. Distinguir JAR jogável do arquivo de ferramentas do fork público.
