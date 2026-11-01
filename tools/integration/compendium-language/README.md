# Teste descartável de idioma do Compêndio

Compile CompendiumProbe.java com destino Java 8 usando Forge e Minecraft no
classpath. Empacote apenas compendiumprobe/*.class em um JAR de teste.
Use somente um cliente Forge 1.7.10 separado, com AM2.5 CR10, Playground e suas
dependências. Ative o pacote PT-BR antes de iniciar o cliente.

O teste cria automaticamente um mundo plano descartável, alterna o idioma real
do jogo, abre os livros, registra screenshots e encerra o processo. Não instale
esse mod de teste em uma instância normal. O resultado é
compendium-language-result.txt no diretório do cliente descartável.

Confere os dois idiomas nos dois livros, categorias, rótulo de receitas,
preservação de flags e número de IDs, recarga de recursos e páginas renderizadas.
