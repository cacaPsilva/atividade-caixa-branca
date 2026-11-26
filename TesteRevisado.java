import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Testes da versão REVISADA. Cada teste usa uma pasta temporária própria.
 * RT09 e RT17 usam test doubles (subclasses que sobrescrevem a abertura dos arquivos)
 * para simular falhas de leitura/escrita que não dá para provocar com arquivos reais.
 */
public class TesteRevisado {

    static int passou = 0, falhou = 0;
    static Path pasta, estoque, movimentos;

    public static void main(String[] args) throws Exception {
        System.out.println("=== Testes da versão REVISADA ===");
        System.out.println("ID   | cenário                 | esperado                         | observado");

        // ---------- consultarQuantidade ----------
        preparar("A1;10\nB2;5\n");
        verificar("RT01", "encontrado 1ª linha", "10", consultar(novo(), "A1"));

        preparar("A1;10\nB2;5\n");
        verificar("RT02", "laço 2 voltas", "5", consultar(novo(), "B2"));

        preparar("A1;10\nB2;5\n");
        verificar("RT03", "não encontrado", "EstoqueException: Produto não encontrado no estoque: Z9",
                consultar(novo(), "Z9"));

        preparar("");
        verificar("RT04", "arquivo vazio", "EstoqueException: Produto não encontrado no estoque: A1",
                consultar(novo(), "A1"));

        preparar(null);
        verificar("RT05", "arquivo inexistente", "IOException propagada",
                tipoDoErro(() -> novo().consultarQuantidade("A1")));

        preparar("A1;abc\n");
        verificar("RT06", "quantidade inválida", "EstoqueException: Linha 1 com quantidade inválida: abc",
                consultar(novo(), "A1"));

        preparar("A1\n");
        verificar("RT07", "linha sem ';'",
                "EstoqueException: Linha 1 do estoque fora do formato codigo;quantidade: A1",
                consultar(novo(), "A1"));

        preparar("A1;10\n");
        verificar("RT08", "código nulo", "IllegalArgumentException: O código do produto é obrigatório.",
                consultar(novo(), null));

        preparar("A1;10\n");
        EstoqueArquivo leituraComFalha = new EstoqueArquivo(estoque, movimentos) {
            @Override
            protected BufferedReader abrirLeitorEstoque() {
                return new BufferedReader(new StringReader("")) {
                    @Override
                    public String readLine() throws IOException {
                        throw new IOException("falha simulada no disco");
                    }
                };
            }
        };
        verificar("RT09", "readLine falha (double)", "IOException: falha simulada no disco",
                consultar(leituraComFalha, "A1"));

        // ---------- registrarSaida ----------
        preparar("A1;10\nB2;5\n");
        EstoqueArquivo e = novo();
        String r = saida(e, "A1", 3);
        verificar("RT10", "saída válida", "true; mov='A1;3'; estoque=7; ultima=A1",
                r + "; mov='" + ler(movimentos) + "'; estoque=" + consultar(novo(), "A1")
                        + "; ultima=" + e.getUltimaMovimentacao());

        preparar("A1;10\n");
        r = saida(novo(), "A1", 10);
        verificar("RT11", "limite = disponível", "true; estoque=0", r + "; estoque=" + consultar(novo(), "A1"));

        preparar("A1;10\n");
        e = novo();
        r = saida(e, "A1", 11);
        verificar("RT12", "limite disponível+1", "false; mov=''; estoque=10; ultima=''",
                r + "; mov='" + ler(movimentos) + "'; estoque=" + consultar(novo(), "A1")
                        + "; ultima='" + e.getUltimaMovimentacao() + "'");

        preparar("A1;10\n");
        e = novo();
        verificar("RT13", "duas saídas de 6", "true e false", saida(e, "A1", 6) + " e " + saida(e, "A1", 6));

        preparar("A1;10\n");
        verificar("RT14", "quantidade negativa",
                "IllegalArgumentException: A quantidade de saída deve ser maior que zero.", saida(novo(), "A1", -5));

        preparar("A1;10\n");
        verificar("RT15", "quantidade zero",
                "IllegalArgumentException: A quantidade de saída deve ser maior que zero.", saida(novo(), "A1", 0));

        preparar("A1;10\n");
        Files.createDirectory(movimentos);
        r = tipoDoErro(() -> novo().registrarSaida("A1", 3));
        verificar("RT16", "movimentações é pasta", "IOException propagada; estoque=10",
                r + "; estoque=" + consultar(novo(), "A1"));

        preparar("A1;10\n");
        EstoqueArquivo escritaComFalha = new EstoqueArquivo(estoque, movimentos) {
            @Override
            protected Writer abrirEscritorMovimentacoes() {
                return new Writer() {
                    public void write(char[] c, int o, int t) throws IOException {
                        throw new IOException("disco cheio (simulado)");
                    }
                    public void flush() { }
                    public void close() { }
                };
            }
        };
        r = saida(escritaComFalha, "A1", 3);
        verificar("RT17", "write falha (double)", "IOException: disco cheio (simulado); estoque=10; ultima=''",
                r + "; estoque=" + consultar(novo(), "A1") + "; ultima='" + escritaComFalha.getUltimaMovimentacao() + "'");

        preparar("A1;10\n");
        verificar("RT18", "código com ';'",
                "IllegalArgumentException: O código não pode conter ';' nem quebra de linha.",
                saida(novo(), "A1;99", 1));

        limpar();
        System.out.println("------------------------------------------------------------");
        System.out.println("Resumo REVISADO: " + passou + " passaram, " + falhou + " falharam");
        if (falhou > 0) System.exit(1);
    }

    // ---- auxiliares ----

    static EstoqueArquivo novo() {
        return new EstoqueArquivo(estoque, movimentos);
    }

    static String consultar(EstoqueArquivo e, String codigo) {
        try {
            return String.valueOf(e.consultarQuantidade(codigo));
        } catch (Exception ex) {
            return ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }
    }

    static String saida(EstoqueArquivo e, String codigo, int qtd) {
        try {
            return String.valueOf(e.registrarSaida(codigo, qtd));
        } catch (Exception ex) {
            return ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }
    }

    interface Acao { Object executar() throws Exception; }

    /** Usado onde a subclasse exata de IOException muda conforme o sistema operacional. */
    static String tipoDoErro(Acao acao) {
        try {
            return "sem erro: " + acao.executar();
        } catch (IOException ex) {
            return "IOException propagada";
        } catch (Exception ex) {
            return ex.getClass().getSimpleName();
        }
    }

    static void verificar(String id, String cenario, String esperado, String observado) {
        boolean ok = esperado.equals(observado);
        if (ok) passou++; else falhou++;
        System.out.printf("%-4s | %-23s | %-32s | %s  => %s%n",
                id, cenario, esperado, observado, ok ? "PASSOU" : "FALHOU");
    }

    static void preparar(String conteudoEstoque) throws Exception {
        limpar();
        pasta = Files.createTempDirectory("estoque-teste");
        estoque = pasta.resolve("estoque.csv");
        movimentos = pasta.resolve("movimentacoes.txt");
        if (conteudoEstoque != null) {
            Files.writeString(estoque, conteudoEstoque);
        }
    }

    static String ler(Path p) throws Exception {
        return Files.isRegularFile(p) ? Files.readString(p).trim() : "";
    }

    static void limpar() throws Exception {
        if (pasta != null && Files.exists(pasta)) {
            try (Stream<Path> s = Files.walk(pasta)) {
                s.sorted(Comparator.reverseOrder()).forEach(x -> x.toFile().delete());
            }
        }
    }
}
