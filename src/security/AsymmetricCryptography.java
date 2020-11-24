package security;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SealedObject;
import javax.crypto.spec.IvParameterSpec;

import communication.UnicastMessage;

public class AsymmetricCryptography {
	
    public static SealedObject encryptPayload(UnicastMessage plainMessage, PublicKey key) {
    	SealedObject encryptedPayload = null;
        // create cipher
    	try {
    		Cipher cipher = Cipher.getInstance("RSA");
	        cipher.init(Cipher.ENCRYPT_MODE, key);
	        encryptedPayload = new SealedObject(plainMessage, cipher);
    	} catch(GeneralSecurityException e) {
    		e.printStackTrace();
    	} catch(IOException e) {
    		e.printStackTrace();
    	}
    	return encryptedPayload;
    }

    public static UnicastMessage decryptPayload(SealedObject sealedObject, PrivateKey key) {
    	UnicastMessage decryptedMessage = null;
    	try {
	    	Cipher cipher = Cipher.getInstance("RSA");
	    	cipher.init(Cipher.DECRYPT_MODE, key);
	        decryptedMessage = (UnicastMessage) sealedObject.getObject(cipher);
	        
	    } catch(GeneralSecurityException | IOException | ClassNotFoundException e) {
			//e.printStackTrace();
		}
    	
    	return decryptedMessage;
    }
}
