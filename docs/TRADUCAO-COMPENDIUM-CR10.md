# Compêndio Arcano em português — CR10

O Compêndio acompanha o idioma do Minecraft. Português (Brasil), código pt_BR,
carrega a tradução. English (US) carrega os textos ingleses originais.
Reabra o livro depois de trocar idioma ou pacote de recursos. Não há um botão
independente de idioma dentro do livro.

## Conteúdo

- AM2.5: 535 entradas, incluindo 474 descrições não vazias.
- Playground: 136 entradas, incluindo 130 descrições não vazias e os requisitos
  avançados adicionados nesta manutenção.
- Nomes de formas, componentes, modificadores, talentos, itens relacionados,
  abas, comandos do livro e instruções foram localizados.
- IDs, referências, condições, números de metadados, receitas, controladores,
  estados de desbloqueio e marcadores de paginação foram preservados.
- Entradas originalmente sem descrição continuam herdando o texto dos pais.
  Passagens marcadas como história em desenvolvimento mantêm essa indicação.
- Combinações secretas de descoberta continuam ocultas. Os guias traduzidos
  mostram os requisitos extras já publicados, não novas receitas secretas.

## Arquivos e instalação

A tradução nativa do AM2.5 acompanha o JAR CR10. Para o Playground, ative também o
[pacote de recursos combinado](https://github.com/Carairritante/am2playground/tree/correcoes/translations).
O pacote contém somente XML, arquivos de idioma e metadados de recursos, sem
classes Java. O JAR do Playground não precisa ser modificado.

1. Coloque o ZIP na pasta resourcepacks da instância.
2. Ative-o em Opções > Pacotes de Recursos, acima de outros que traduzam o AM2.
3. Em Opções > Idioma, selecione Português (Brasil).
4. Reabra o Compêndio.

O pacote foi preparado para nossa manutenção AM2.5 CR9/CR10 e Playground
advanced-silver-progression. Na CR9, os textos podem ser usados reiniciando o
cliente, mas alguns rótulos fixos continuam em inglês. A CR10 elimina essa
limitação e permite a troca durante a sessão. Não substitua versões do mod em
outros packs sem verificar a compatibilidade das regras descritas.

## Código alterado no AM2.5

As abas e dois rótulos eram fixos em inglês. Agora usam chaves de idioma, com
fallback do nome das categorias para addons. Um listener marca a necessidade de
recarregar recursos, e a abertura do livro atualiza texto e entradas dos addons.
Flags locais de descoberta e novidade são preservadas por ID, inclusive em
subpáginas, e o histórico de navegação antigo é limpo para evitar títulos antigos.
Não altera receitas, magias, mana, recompensas ou regras de aprendizagem.

## Validação

- Compilação e verificadores anteriores do AM2.5 passaram.
- Verificação estrutural de todas as entradas e dos códigos de formatação.
- Testes do mesclador de requisitos em português, incluindo o marcador antigo
  e reaplicação sem duplicações.
- Cliente completo: 653 IDs de entradas/subentradas registradas, alternância
  EN -> PT -> EN, categorias traduzidas e estados de descoberta preservados.
- Cliente mínimo com Playground: 622 IDs, mesmo ciclo de idioma e recursos.
  As diferenças de quantidade são das integrações opcionais, não da tradução.
- Páginas do índice, primeira magia, requisitos do Playground e retorno ao inglês
  renderizadas e inspecionadas. Inglês não é substituído pelo pacote.

Créditos originais de Mithion, TCLProject, DrParadox7 e AirBurn0 preservados.
Tradução de manutenção por Carairritante com participação substancial do OpenAI Codex.
