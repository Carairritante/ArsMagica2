# Menu do Giz do Arquiteto Arcano — CR16

O menu usa o mesmo tema azul-escuro da HUD da CR15: painel com bordas azuis
luminosas, runas originais do Wizard Chalk e realce do ritual selecionado. Os
nomes longos podem ser lidos por completo ao passar o mouse sobre a opção.

Os botões de páginas, rotação, limpeza do centro e montagem seguem o mesmo
tema. O menu ajusta a quantidade de linhas à janela e preserva uma página
válida quando a resolução muda. Ao abrir, mostra a página do ritual selecionado.

As ações e permissões são preservadas. No survival, a ferramenta continua
oferecendo projeções; montar a estrutura automaticamente exige modo criativo.
Os rituais ainda precisam de seus ingredientes e de sua ativação normal.

## Validação local

- Build Gradle e verificações de manutenção concluídos com sucesso.
- Cliente integrado Forge 1.7.10, sem Playground e com o Playground corrigido
  Beta 0.12.1 independent-silver, em mundos descartáveis.
- Abertura por clique direito normal; seleção em outra página e rotação com
  sincronização entre cliente e servidor; limpeza e nova marcação do centro.
- Montagem desabilitada no survival e recusa do servidor preservada; montagem
  pelo menu validada no criativo.
- Capturas em 1024 × 768 e 640 × 480, com escala de interface 2, e navegação
  após redimensionar a última página do catálogo.

São testes locais. Este documento não afirma execução do GitHub Actions nem
compatibilidade universal com outras versões. O Playground não foi modificado
para esta atualização visual.

## Ajustes locais da HUD — CR18

O botão de configuração no canto superior direito do menu abre a prévia da HUD.
Arraste o painel para posicioná-lo. Os controles de tamanho vão de 50% a 150%;
os de transparência do fundo vão de 0% a 100%, mantendo os textos legíveis.
O botão Restaurar volta ao canto superior esquerdo, tamanho 100% e transparência
45%. Concluir e Esc salvam os ajustes localmente.

Validado com nossa CR18 em cópia completa do Forbidden Series: abertura pelo
botão, arraste, tamanho, transparência, recarga do arquivo salvo, restauração,
limites e janela menor. O item do servidor e a restrição de montagem no survival
foram preservados; a instância original permaneceu inalterada.
