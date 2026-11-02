# Categorias opcionais do Compêndio — CR12

O Compêndio tinha uma lista fixa de categorias. Seu método público de extensão
criava apenas entradas de itens, blocos, entidades e partes de magias nas
categorias existentes. Um addon não conseguia registrar sua própria área e uma
página de estrutura personalizada por esse caminho.

CompendiumEntryTypes.registerCategory(type) acrescenta uma categoria ao final
das categorias originais. Repetir o registro do mesmo objeto é idempotente;
outro objeto com o mesmo nome é rejeitado. As listas retornadas são cópias e os
tipos originais permanecem na mesma ordem.

ArcaneCompendium.registerAddonEntry(entry) aceita uma entrada já construída,
com categoria previamente registrada, ID exclusivo e GUI própria. Não substitui
entradas ou aliases existentes. O registro de entradas deve ocorrer no evento
cliente RegisterCompendiumEntries, repetido quando o idioma é atualizado.

O AM2.5 não registra conteúdo de Arcane Engineering por conta própria: o addon
permanece opcional e suas regras de descoberta e acesso pertencem a ele.
Sem addons que utilizem a extensão, o catálogo original permanece igual.

A implementação foi exercitada pelo Arcane Engineering 0.1.0 em cliente
mínimo, modpack completo e inicialização de servidor dedicado. As verificações
de manutenção do AM2.5 e a validação das 535 entradas PT-BR passaram.

Participação substancial do OpenAI Codex na implementação e validação, mantendo
os créditos e a autoria original do AM2.5.
