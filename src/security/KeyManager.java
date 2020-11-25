package security;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
/*
 * This class stores both the private and public keys of all the relays.
 * Its purpose is also to generate these keys when the simulation is started
 */
public class KeyManager {

    private static KeyPairGenerator keyGen;
    public static HashMap<Integer, PrivateKey> PRIVATE_KEYS; 
    public static HashMap<Integer, PublicKey> PUBLIC_KEYS;

    public static void initialize(int keylength, int keyNumber) throws NoSuchAlgorithmException, NoSuchProviderException {
		keyGen = KeyPairGenerator.getInstance("RSA");
	    keyGen.initialize(keylength);
	    PRIVATE_KEYS = new HashMap<>();
	    PUBLIC_KEYS = new HashMap<>();
         
	    //Generate the keys for the initial nodes
        for(int i=0; i<keyNumber; i++) {
        	generateKeys(i);
    	}
    }
    
    /*
     * Generate a number of key pairs equal to the number of agents
     */
    public static void generateKeys(int relayId) {
    	KeyPair pair = keyGen.generateKeyPair();
        PRIVATE_KEYS.put(relayId, pair.getPrivate());
        PUBLIC_KEYS.put(relayId, pair.getPublic());
    }
}