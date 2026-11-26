import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Testes do código ORIGINAL (sem nenhuma alteração nele).
 *
 * O original usa os nomes fixos "estoque.csv" e "movimentacoes.txt" na pasta atual,
 * por isso o script executa este programa dentro de uma pasta de trabalho vazia.
 *
 * O "esperado" é o comportamento correto de um controle de estoque. Quando o legado
 * não atende, o teste aparece como FALHOU de propósito: é a evidência do defeito.
 */
public class TesteOriginal {

    static final Path ESTOQUE = Paths.get("estoque.csv");
    static final Path MOVIMENTOS = Paths.get("movimentacoes.txt");
    static int passou = 0, falhou = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("=== Testes do código ORIGINAL ===");
        System.out.println("ID   | caminho                 | esperado                         | observado");

        // ---------- consultarQuantidade ----------
        preparar("A1;10\nB2;5\n");
        verificar("CT01", "CQ-C3", "10", consultar("A1"));

        preparar("A1;10\nB2;5\n");
        verificar("CT02", "CQ-C4+C3 (2 voltas)", "5", consultar("B2"));

        preparar("A1;10\nB2;5\n");
        verificar("CT03", "CQ-C4", "0", consultar("Z9"));

        preparar("");
        verificar("CT04", "CQ-C2", "0", consultar("A1"));

        preparar(null); // sem estoque.csv
        verificar("CT05", "CQ-C1", "erro de leitura informado", consultar("A1"));

        preparar("A1;abc\n");
        verificar("CT06", "exceção não modelada", "erro tratado c/ msg clara", consultar("A1"));

        preparar("A1\n");
        verificar("CT07", "exceção não modelada", "erro tratado c/ msg clara", consultar("A1"));

        preparar("A1;10\n");
        verificar("CT08", "CQ-C4 (entrada nula)", "IllegalArgumentException", consultar(null));

        // ---------- registrarSaida ----------
        preparar("A1;10\n");
        EstoqueArquivo e = new EstoqueArquivo();
        String r = String.valueOf(e.registrarSaida("A1", 3));
        verificar("CT09", "RS-C2", "true; arquivo='A1;3'",
                r + "; arquivo='" + lerMovimentos() + "'; ultima=" + e.getUltimaMovimentacao());

        preparar("A1;10\n");
        verificar("CT10", "RS-C2 (limite =)", "true", String.valueOf(new EstoqueArquivo().registrarSaida("A1", 10)));

        preparar("A1;10\n");
        e = new EstoqueArquivo();
        r = String.valueOf(e.registrarSaida("A1", 11));
        verificar("CT11", "RS-C1 (limite +1)", "false; ultima=''", r + "; ultima='" + e.getUltimaMovimentacao() + "'");

        preparar("A1;10\n");
        e = new EstoqueArquivo();
        r = e.registrarSaida("A1", 6) + " e " + e.registrarSaida("A1", 6);
        verificar("CT12", "RS-C2 depois RS-C1", "true e false", r);

        preparar("A1;10\n");
        verificar("CT13", "RS-C2 (negativo)", "IllegalArgumentException", saida("A1", -5));

        preparar("A1;10\n");
        verificar("CT14", "RS-C2 (zero)", "IllegalArgumentException", saida("A1", 0));

        preparar("A1;10\n");
        Files.createDirectory(MOVIMENTOS); // FileWriter não consegue abrir uma pasta
        verificar("CT15", "RS-C3", "erro de escrita informado", saida("A1", 3));

        limpar();
        System.out.println("------------------------------------------------------------");
        System.out.println("Resumo ORIGINAL: " + passou + " passaram, " + falhou + " falharam");
        System.out.println("CT-NE1 (CQ-C5) e CT-NE2 (RS-C4) não são executáveis no original: ver README.");
    }

    // ---- auxiliares ----

    static String consultar(String codigo) {
        try {
            return String.valueOf(new EstoqueArquivo().consultarQuantidade(codigo));
        } catch (Exception ex) {
            return ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }
    }

    static String saida(String codigo, int qtd) {
        try {
            return String.valueOf(new EstoqueArquivo().registrarSaida(codigo, qtd));
        } catch (Exception ex) {
            return ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }
    }

    static void verificar(String id, String caminho, String esperado, String observado) {
        boolean ok = esperado.equals(observado);
        if (ok) passou++; else falhou++;
        System.out.printf("%-4s | %-23s | %-32s | %s  => %s%n",
                id, caminho, esperado, observado, ok ? "PASSOU" : "FALHOU");
    }

    static void preparar(String conteudoEstoque) throws Exception {
        limpar();
        if (conteudoEstoque != null) {
            Files.writeString(ESTOQUE, conteudoEstoque);
        }
    }

    static String lerMovimentos() throws Exception {
        return Files.exists(MOVIMENTOS) ? Files.readString(MOVIMENTOS).trim() : "";
    }

    static void limpar() throws Exception {
        for (Path p : new Path[] {ESTOQUE, MOVIMENTOS}) {
            if (Files.exists(p)) {
                try (Stream<Path> s = Files.walk(p)) {
                    s.sorted(Comparator.reverseOrder()).forEach(x -> x.toFile().delete());
                }
            }
        }
    }
}
