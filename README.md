# Teste de Caixa Branca – Estoque por arquivo CSV

**Aluno:** Caique Pereira da Silva – RA 251874
**Professor:** Daniel Ohata
**Disciplina:** UX/UI e Testes de Software

---

## 1. Introdução

Cenário 2: a classe `EstoqueArquivo` tem dois métodos principais:

- `consultarQuantidade(codigo)`: lê o `estoque.csv` (`codigo;quantidade`) e retorna a quantidade do produto;
- `registrarSaida(codigo, quantidade)`: consulta o saldo e, se houver, grava a saída em `movimentacoes.txt`.

O trabalho revisa o código, monta os grafos, calcula a complexidade ciclomática, testa o original e compara com uma versão corrigida.

```
atividade-caixa-branca/
├── original/      código-base sem alterações
├── revisado/      código corrigido
├── testes/        TesteOriginal.java e TesteRevisado.java
├── diagramas/     grafos (.dot e .png)
├── evidencias/    saída dos testes
├── PLANO_DE_TESTE.xlsx
└── README.md
```

## 2. Como executar

Requisito: JDK 11 ou superior. Não precisa de bibliotecas externas.

- Windows: `executar-testes.bat`
- Linux/macOS/Git Bash: `./executar-testes.sh`

O script compila o original e o revisado em pastas separadas, roda os dois programas de teste e salva as saídas em `evidencias/`.

## 3. Análise estática

A análise completa está na planilha `PLANO_DE_TESTE.xlsx`, aba **CAIXA BRANCA (ESTÁTICO)**. Principais problemas:

| Problema | Linha | Severidade |
|---|---|---|
| `FileWriter` não é fechado: retorna `true`, mas não grava nada. `BufferedReader` também fica aberto | 13, 32-33 | Crítica |
| `catch` vazios: erro de arquivo vira 0 ou `false` | 22-24, 36-38 | Alta |
| Aceita quantidade negativa ou zero e código com `;` | 28-30, 33 | Alta |
| A saída não diminui o saldo do estoque | 29-35 | Alta |
| Linha mal formatada gera exceção sem tratamento | 16-18 | Média |
| Retorno 0 é ambíguo (zerado, inexistente ou erro) | 11, 25 | Média |
| Nomes de arquivo fixos no código | 13, 32 | Média |
| Métodos sem documentação | 10, 28 | Baixa |

## 4. Grafos

**Convenções:** cada nó é um comando ou bloco. Considerei `if`, `while` (incluindo a saída do laço), `break` e `return`. Modelei só as exceções que o código captura (`IOException`), que aparecem como arestas tracejadas. `NumberFormatException` e `ArrayIndexOutOfBoundsException` ficaram fora do grafo porque não são capturadas, mas foram testadas (CT06 e CT07).

### `consultarQuantidade`

![Grafo consultarQuantidade](diagramas/grafo-consultarQuantidade.png)

| Nó | Linha | Comando |
|---|---|---|
| 1 | 11 | `quantidade = 0` |
| 2 | 13 | abre o arquivo |
| 3 | 15 | `while (readLine() != null)` |
| 4 | 16 | `split(";")` |
| 5 | 17 | `if (colunas[0].equals(codigo))` |
| 6 | 18-19 | `parseInt` + `break` |
| 7 | 22-23 | `catch` vazio |
| 8 | 25 | `return quantidade` |

### `registrarSaida`

![Grafo registrarSaida](diagramas/grafo-registrarSaida.png)

| Nó | Linha | Comando |
|---|---|---|
| 1 | 29 | `consultarQuantidade(codigo)` |
| 2 | 30 | `if (quantidade <= disponivel)` |
| 3 | 32 | `new FileWriter(...)` |
| 4 | 33 | `write(...)` |
| 5 | 34-35 | `return true` |
| 6 | 36-37 | `catch` vazio |
| 7 | 40 | `return false` |
| 8 | – | saída do método |

## 5. Complexidade ciclomática

V(G) = E − N + 2P, com P = 1.

| Método | N | E | V(G) |
|---|---|---|---|
| `consultarQuantidade` | 8 | 11 | 11 − 8 + 2 = **5** |
| `registrarSaida` | 8 | 10 | 10 − 8 + 2 = **4** |

**Conferência por decisões (D + 1):**
- `consultarQuantidade`: abrir arquivo (1) + `while` com exceção (2) + `if` (1) = 4, então V(G) = 5;
- `registrarSaida`: `if` (1) + abrir arquivo (1) + `write` (1) = 3, então V(G) = 4.

A contagem de regiões dá o mesmo resultado: 5 e 4, incluindo a região externa.

## 6. Caminhos básicos

### `consultarQuantidade` (5 caminhos)

| Caminho | Nós | Quando acontece |
|---|---|---|
| C1 | 1-2-7-8 | arquivo não existe |
| C2 | 1-2-3-8 | arquivo vazio |
| C3 | 1-2-3-4-5-6-8 | código na 1ª linha |
| C4 | 1-2-3-4-5-3-8 | código não encontrado |
| C5 | 1-2-3-7-8 | erro no `readLine()` |

### `registrarSaida` (4 caminhos)

| Caminho | Nós | Quando acontece |
|---|---|---|
| C1 | 1-2-7-8 | saldo insuficiente |
| C2 | 1-2-3-4-5-8 | saída gravada |
| C3 | 1-2-3-6-7-8 | não consegue abrir o arquivo |
| C4 | 1-2-3-4-6-7-8 | erro no `write()` |

**Interação entre os métodos:** `registrarSaida` usa o retorno da consulta. Como a consulta retorna 0 em vários casos (inclusive em erro), uma saída de quantidade 0 é aceita mesmo quando o arquivo nem existe.

## 7. Casos de teste

O "esperado" é o comportamento correto. Quando o original não atende, o teste falha, e isso serve de evidência do defeito.

### Original

| ID | Método | Caminho | Entrada | Pré-condição | Esperado | Observado | Situação |
|---|---|---|---|---|---|---|---|
| CT01 | consultar | C3 | `A1` | `A1;10` | 10 | 10 | Passou |
| CT02 | consultar | C4+C3 | `B2` | `A1;10` / `B2;5` | 5 | 5 | Passou |
| CT03 | consultar | C4 | `Z9` | `A1;10` | 0 | 0 | Passou |
| CT04 | consultar | C2 | `A1` | arquivo vazio | 0 | 0 | Passou |
| CT05 | consultar | C1 | `A1` | sem arquivo | erro informado | 0 | Falhou |
| CT06 | consultar | exceção | `A1` | `A1;abc` | erro tratado | `NumberFormatException` | Falhou |
| CT07 | consultar | exceção | `A1` | `A1` | erro tratado | `ArrayIndexOutOfBounds` | Falhou |
| CT08 | consultar | C4 | `null` | `A1;10` | erro de entrada | 0 | Falhou |
| CT09 | registrar | C2 | `A1`, 3 | `A1;10` | `true` + arquivo gravado | `true`, arquivo vazio | Falhou |
| CT10 | registrar | C2 | `A1`, 10 | `A1;10` | `true` | `true` | Passou |
| CT11 | registrar | C1 | `A1`, 11 | `A1;10` | `false` | `false` | Passou |
| CT12 | registrar | C2, C1 | `A1`, 6 duas vezes | `A1;10` | `true` e `false` | `true` e `true` | Falhou |
| CT13 | registrar | C2 | `A1`, -5 | `A1;10` | erro de entrada | `true` | Falhou |
| CT14 | registrar | C2 | `A1`, 0 | `A1;10` | erro de entrada | `true` | Falhou |
| CT15 | registrar | C3 | `A1`, 3 | `movimentacoes.txt` é pasta | erro informado | `false` | Falhou |

Resultado: 6 passaram e 9 falharam. Os caminhos C5 (consulta) e C4 (registro) não dão para provocar no original.
Evidência: `evidencias/saida-testes-original.txt`

### Revisado

Os mesmos cenários foram repetidos na versão corrigida (RT01 a RT18), além de três testes novos:
- **RT09:** simula erro no `readLine()`;
- **RT17:** simula erro no `write()`;
- **RT18:** bloqueia código com `;`.

**Resultado: 18 passaram e 0 falharam.**
Evidência: `evidencias/saida-testes-revisado.txt`

**Caminhos difíceis de testar:** erro no `readLine()` e no `write()` dependem de falha de disco. No revisado, a abertura dos arquivos ficou em métodos `protected`. Assim, o teste cria uma subclasse que devolve um leitor ou escritor falso que lança `IOException` (test double). Também daria para usar Mockito.

## 8. Correções e comparação

| Problema | Correção | Teste |
|---|---|---|
| Arquivos não fechados | `try-with-resources` | RT10 |
| `catch` vazios | erros são repassados | RT05, RT09, RT16, RT17 |
| Linha mal formatada | erro com o número da linha | RT06, RT07 |
| Entradas sem validação | código obrigatório e sem `;`; quantidade > 0 | RT08, RT14, RT15, RT18 |
| Retorno 0 ambíguo | produto inexistente gera exceção | RT03, RT04 |
| Saldo não diminuía | o estoque é atualizado após a saída | RT10, RT13 |
| Caminhos fixos | caminhos recebidos no construtor | todos os RT |

| Aspecto | Original | Revisado |
|---|---|---|
| Segurança | código gravado sem validação | entrada validada |
| Confiabilidade | não grava a saída nem atualiza o saldo | grava e atualiza; desfaz a baixa se a gravação falhar |
| Erros | escondidos | informados com mensagem clara |
| Legibilidade | sem documentação | métodos documentados |
| V(G) | 5 e 4 | 6 e 6 |

A complexidade subiu um pouco por causa das validações e do tratamento de erro, mas o código ficou mais seguro e confiável.

## 9. Limitações

- Os testes são um programa Java simples, não JUnit, e não medi a cobertura com ferramenta.
- Os erros de `readLine()` e `write()` só foram testados com simulação, na versão revisada.
- A atualização do estoque e a gravação da movimentação não são uma transação completa.
- O `synchronized` só protege dentro do mesmo programa.

## 10. Conclusão

O original funciona na consulta básica, mas tem falhas graves: diz que registrou a saída sem gravar nada, não diminui o saldo, aceita quantidades inválidas e esconde erros de arquivo. A versão revisada corrige esses pontos e passa em todos os testes.

### Referências

- PRESSMAN, R. S.; MAXIM, B. R. *Engenharia de Software: Uma Abordagem Profissional*. 8. ed. AMGH, 2016.
- Material da Aula 10 – Testes de Caixa Branca.
