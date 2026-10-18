import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Executado no terminal do operador; não recebe senha por argumento. */
public class GerarHashSenha {
    public static void main(String[] args) {
        var console = System.console();
        if (console == null) {
            throw new IllegalStateException("Execute em um terminal interativo, sem redirecionar a entrada.");
        }
        char[] senha = console.readPassword("Nova senha da PLATAFORMA: ");
        char[] confirmacao = console.readPassword("Confirme a senha: ");
        try {
            if (senha == null || confirmacao == null || !Arrays.equals(senha, confirmacao)) {
                throw new IllegalArgumentException("As senhas não conferem.");
            }
            String texto = new String(senha);
            if (senha.length < 12 || texto.getBytes(StandardCharsets.UTF_8).length > 72) {
                throw new IllegalArgumentException("Use pelo menos 12 caracteres e no máximo 72 bytes UTF-8.");
            }
            console.printf("Hash para uso privado no SQL Editor:%n%s%n", new BCryptPasswordEncoder(12).encode(texto));
        } finally {
            if (senha != null) Arrays.fill(senha, '\0');
            if (confirmacao != null) Arrays.fill(confirmacao, '\0');
        }
    }
}
