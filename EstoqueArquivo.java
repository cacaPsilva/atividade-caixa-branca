import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Controle de estoque baseado em arquivo (versão revisada).
 *
 * Arquivo de estoque: uma linha por produto no formato codigo;quantidade
 * Arquivo de movimentações: cada saída registrada vira uma linha codigo;quantidade
 *
 * Principais mudanças em relação ao original:
 * - arquivos abertos com try-with-resources (sempre fechados, dados gravados de verdade);
 * - erros de leitura/escrita não são mais engolidos: são repassados para quem chamou;
 * - código e quantidade são validados antes de qualquer acesso a arquivo;
 * - a saída agora abate o saldo no arquivo de estoque.
 */
public class EstoqueArquivo {

    private final Path arquivoEstoque;
    private final Path arquivoMovimentacoes;
    private String ultimaMovimentacao = "";

    /** Mantém os mesmos nomes de arquivo do código original. */
    public EstoqueArquivo() {
        this(Paths.get("estoque.csv"), Paths.get("movimentacoes.txt"));
    }

    /** Permite informar os arquivos (usado nos testes, evita depender da pasta atual). */
    public EstoqueArquivo(Path arquivoEstoque, Path arquivoMovimentacoes) {
        if (arquivoEstoque == null || arquivoMovimentacoes == null) {
            throw new IllegalArgumentException("Os caminhos dos arquivos são obrigatórios.");
        }
        this.arquivoEstoque = arquivoEstoque;
        this.arquivoMovimentacoes = arquivoMovimentacoes;
    }

    /**
     * Consulta a quantidade disponível de um produto.
     *
     * @param codigo código do produto (não pode ser vazio nem conter ';' ou quebra de linha)
     * @return quantidade em estoque (zero significa que o produto existe, mas está zerado)
     * @throws IllegalArgumentException código inválido
     * @throws EstoqueException produto não encontrado ou linha do arquivo fora do formato
     * @throws IOException falha ao abrir ou ler o arquivo de estoque
     */
    public int consultarQuantidade(String codigo) throws IOException {
        validarCodigo(codigo);

        // try-with-resources: o leitor é fechado mesmo se der erro no meio da leitura.
        try (BufferedReader leitor = abrirLeitorEstoque()) {
            String linha;
            int numeroLinha = 0;
            while ((linha = leitor.readLine()) != null) {
                numeroLinha++;
                if (linha.isBlank()) {
                    continue; // linha vazia no fim do arquivo não é erro
                }
                String[] colunas = lerColunas(linha, numeroLinha);
                if (colunas[0].equals(codigo)) {
                    return converterQuantidade(colunas[1], numeroLinha);
                }
            }
        }
        // Antes retornava 0, igual a um produto zerado. Agora o caso fica explícito.
        throw new EstoqueException("Produto não encontrado no estoque: " + codigo);
    }

    /**
     * Registra a saída de um produto: abate o saldo no estoque e grava a movimentação.
     *
     * @return true se a saída foi registrada; false se o saldo é insuficiente
     * @throws IllegalArgumentException código inválido ou quantidade menor ou igual a zero
     * @throws EstoqueException produto não encontrado ou arquivo de estoque inválido
     * @throws IOException falha ao ler/gravar os arquivos (o saldo é restaurado se a
     *                     gravação da movimentação falhar)
     */
    public synchronized boolean registrarSaida(String codigo, int quantidade) throws IOException {
        validarCodigo(codigo);
        if (quantidade <= 0) {
            throw new IllegalArgumentException("A quantidade de saída deve ser maior que zero.");
        }

        int disponivel = consultarQuantidade(codigo);
        if (quantidade > disponivel) {
            return false; // saldo insuficiente é regra de negócio, não erro técnico
        }

        atualizarEstoque(codigo, disponivel - quantidade);
        try (Writer escrita = abrirEscritorMovimentacoes()) {
            escrita.write(codigo + ";" + quantidade + "\n");
        } catch (IOException e) {
            // Não engole o erro: desfaz a baixa para estoque e histórico não ficarem
            // diferentes e repassa a exceção original.
            try {
                atualizarEstoque(codigo, disponivel);
            } catch (IOException erroAoDesfazer) {
                e.addSuppressed(erroAoDesfazer);
            }
            throw e;
        }

        ultimaMovimentacao = codigo; // só muda depois que tudo deu certo
        return true;
    }

    public String getUltimaMovimentacao() {
        return ultimaMovimentacao;
    }

    // ---------------------------------------------------------------------------------
    // Pontos de acesso a arquivo. São protected para que os testes possam simular falhas
    // de leitura/escrita (test double), que são difíceis de provocar com arquivos reais.
    // ---------------------------------------------------------------------------------

    protected BufferedReader abrirLeitorEstoque() throws IOException {
        return Files.newBufferedReader(arquivoEstoque, StandardCharsets.UTF_8);
    }

    protected Writer abrirEscritorMovimentacoes() throws IOException {
        return Files.newBufferedWriter(arquivoMovimentacoes, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    // ---------------------------------------------------------------------------------
    // Métodos auxiliares
    // ---------------------------------------------------------------------------------

    private void validarCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("O código do produto é obrigatório.");
        }
        // ';' e quebras de linha quebrariam o formato dos arquivos (injeção de linhas).
        if (codigo.contains(";") || codigo.contains("\n") || codigo.contains("\r")) {
            throw new IllegalArgumentException("O código não pode conter ';' nem quebra de linha.");
        }
    }

    private String[] lerColunas(String linha, int numeroLinha) {
        String[] colunas = linha.split(";");
        if (colunas.length != 2) {
            throw new EstoqueException("Linha " + numeroLinha
                    + " do estoque fora do formato codigo;quantidade: " + linha);
        }
        colunas[0] = colunas[0].trim();
        colunas[1] = colunas[1].trim();
        return colunas;
    }

    private int converterQuantidade(String texto, int numeroLinha) {
        try {
            int quantidade = Integer.parseInt(texto);
            if (quantidade < 0) {
                throw new EstoqueException("Linha " + numeroLinha + " com quantidade negativa: " + texto);
            }
            return quantidade;
        } catch (NumberFormatException e) {
            throw new EstoqueException("Linha " + numeroLinha + " com quantidade inválida: " + texto, e);
        }
    }

    /** Reescreve o arquivo de estoque com o novo saldo do produto (via arquivo temporário). */
    private void atualizarEstoque(String codigo, int novaQuantidade) throws IOException {
        List<String> linhas = new ArrayList<>();
        try (BufferedReader leitor = abrirLeitorEstoque()) {
            String linha;
            while ((linha = leitor.readLine()) != null) {
                if (!linha.isBlank() && linha.split(";")[0].trim().equals(codigo)) {
                    linha = codigo + ";" + novaQuantidade;
                }
                linhas.add(linha);
            }
        }
        Path pasta = arquivoEstoque.toAbsolutePath().getParent();
        Path temporario = Files.createTempFile(pasta, "estoque", ".tmp");
        try {
            Files.write(temporario, linhas, StandardCharsets.UTF_8);
            Files.move(temporario, arquivoEstoque, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporario);
        }
    }
}
