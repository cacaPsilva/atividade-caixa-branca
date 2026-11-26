/**
 * Erro de regra/dados do estoque (produto inexistente ou linha do CSV fora do formato).
 * Separado de IOException para que quem chama saiba diferenciar "arquivo com problema"
 * de "dado com problema".
 */
public class EstoqueException extends RuntimeException {
    public EstoqueException(String mensagem) {
        super(mensagem);
    }

    public EstoqueException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
