import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;

public class EncryptPassword {
    public static void main(String[] args) {
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();

        // Set the encryption password (this should be kept secret)
        String encryptionKey = "mySecretKey123"; // Change this to your own secret key
        encryptor.setPassword(encryptionKey);
        encryptor.setAlgorithm("PBEWithMD5AndDES");

        // The database password to encrypt
        String password = "123456";

        // Encrypt the password
        String encryptedPassword = encryptor.encrypt(password);

        System.out.println("======================================");
        System.out.println("Encryption Key: " + encryptionKey);
        System.out.println("Original Password: " + password);
        System.out.println("Encrypted Password: " + encryptedPassword);
        System.out.println("======================================");
        System.out.println("\nAdd this to your application-config.yml:");
        System.out.println("password: ENC(" + encryptedPassword + ")");
        System.out.println("\nAnd add this to your application.yml:");
        System.out.println("jasypt:");
        System.out.println("  encryptor:");
        System.out.println("    password: " + encryptionKey);
        System.out.println("    algorithm: PBEWithMD5AndDES");
    }
}
