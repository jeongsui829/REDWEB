// Run with: java -cp target/classes scripts/verification/PasswordBenchmark.java
// Prints timing only. Synthetic value; never uses an account credential.
import com.scuredweb.common.security.PasswordUtil;
public class PasswordBenchmark {
    public static void main(String[] args) {
        for (int i=0;i<3;i++) PasswordUtil.hash("synthetic benchmark passphrase");
        long start=System.nanoTime();
        for (int i=0;i<10;i++) PasswordUtil.hash("synthetic benchmark passphrase");
        System.out.printf("PBKDF2 target mean milliseconds: %.1f%n",(System.nanoTime()-start)/10000000.0);
    }
}
