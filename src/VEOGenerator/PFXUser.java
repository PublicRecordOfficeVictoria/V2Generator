package VEOGenerator;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

/**
 * *************************************************************
 *
 * P F X U S E R
 *
 * This class encapsulates the information about a user encapsulated in a
 * PKCS#12 file (file extension normally .pfx). A PKCS#12 file contains a
 * private key and a certificate chain.
 *
 * Andrew Waugh (andrew.waugh@dvc.vic.gov.au) Copyright 2006 PROV
 *
 *************************************************************
 */

/**
 * This class represents the contents of a PFX#12 file. This file contains
 * public key cryptography information about a user, particularly the private
 * key of the user and the certificate chain used to validate objects signed
 * with the private key.
 */
public final class PFXUser {

    KeyStore ks;
    char[] password;
    String alias;
    java.security.cert.Certificate[] certificateChain;
    PublicKey pubKey;
    PrivateKey priKey;

    /**
     * Constructor
     *
     * Construct an instance of this class from a PKCS#12 file. Note that if the
     * password is incorrect, you may get an IOException complaining about an
     * invalid pad byte.
     *
     * @param pfxfile	The name of the PKCS#12 file
     * @param passwd	The password of the file used to access the private key
     * @throws VEOError
     */
    public PFXUser(String pfxfile, String passwd) throws VEOError {
        FileInputStream fis;
        String s;
        Enumeration aliases;
        int i;
        String name = "PFXUser(): ";

        // get PKCS12 keystore
        try {
            ks = KeyStore.getInstance("PKCS12");
        } catch (KeyStoreException e) {
            throw new VEOError(name + e.getMessage());
        }

        // open pkcs12 file
        fis = null;
        try {
            fis = new FileInputStream(pfxfile);
        } catch (FileNotFoundException e) {
            throw new VEOError(name
                    + "PFX file '" + pfxfile + "' not found");
        }

        // extract details from file
        password = new char[passwd.length()];
        for (i = 0; i < passwd.length(); i++) {
            password[i] = passwd.charAt(i);
        }
        try {
            ks.load(fis, password);
        } catch (IOException e) {
            throw new VEOError(name + "IOException: " + e.getMessage());
        } catch (NoSuchAlgorithmException nsae) {
            throw new VEOError(name
                    + "NoSuchAlgorithmException: " + nsae.getMessage());
        } catch (CertificateException ce) {
            throw new VEOError(name
                    + "CertificateException: " + ce.getMessage());
        }

        // check to see how many private keys this PKCS file holds
        try {
            aliases = ks.aliases();
            i = 0;
            while (aliases.hasMoreElements()) {
                i++;
                aliases.nextElement();
            }
            if (i == 0) {
                throw new VEOError(name + "No private key in PFX file");
            }
            if (i > 1) {
                System.err.println("More than one private key in PFX file");
                i = 0;
                aliases = ks.aliases();
                while (aliases.hasMoreElements()) {
                    i++;
                    System.err.println(aliases.nextElement());
                }
                System.err.println("Will use first key");
            }
            aliases = ks.aliases();
            alias = (String) aliases.nextElement();
        } catch (KeyStoreException e) {
            throw new VEOError(name
                    + "KeyStoreException: " + e.getMessage());
        }

        // get first private key
        try {
            aliases = ks.aliases();
            alias = (String) aliases.nextElement();
            if (!ks.isKeyEntry(alias)) {
                throw new VEOError(name
                        + "PFX file does not contain a key entry as the first entry");
            }
        } catch (KeyStoreException e) {
            throw new VEOError(name + e.getMessage());
        }

        // get the certificate chain
        try {
            certificateChain = ks.getCertificateChain(alias);
        } catch (KeyStoreException e) {
            throw new VEOError(name
                    + "KeyStoreException: " + e.getMessage());
        }

        // get the public and private key
        try {
            priKey = (PrivateKey) ks.getKey(alias, password);
        } catch (KeyStoreException e) {
            throw new VEOError(name
                    + "KeyStoreException: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            throw new VEOError(name
                    + "NoSuchAlgorithmException: " + e.getMessage());
        } catch (UnrecoverableKeyException e) {
            throw new VEOError(name
                    + "UnrecoverableKeyException: " + e.getMessage());
        }
        pubKey = (PublicKey) (getX509Certificate(0).getPublicKey());
    }

    /**
     * Get String representation of the user details.
     *
     * @return a String representation of the information in the PFX file.
     */
    @Override
    public String toString() {
        StringBuffer sb;
        int i;

        sb = new StringBuffer();
        sb.append(pubKey.toString());
        sb.append("\n");
        sb.append(priKey.toString());
        sb.append("\n");
        sb.append("Certificate Chain:\n");
        for (i = 0; i < certificateChain.length; i++) {
            try {
                sb.append(getX509Certificate(i).toString());
            } catch (VEOError e) {
                /* ignore */ }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Return the length of the certificate chain.
     *
     * @return the length of the certificate chain.
     */
    public int getCertificateChainLength() {
        return certificateChain.length;
    }

    /**
     * Return the ith certificate from the certificate chain represented as an
     * ASN.1 DER encoded byte array. The first certificate is numbered 0. The
     * method throws a VEOError if the certificate requested is negative, or
     * greater than the number of certificates in the PFX file.
     *
     * @param i index of requested certificate
     * @return the DER encoded certificate in a byte array
     * @throws VEOError Gets the i'th Certificate from the certificate chain
     * encoded. The first Return an element from the certificate chain.
     */
    public byte[] getCertificate(int i) throws VEOError {
        byte[] b;
        String name = "PFXUser.getCertificateFromChain(): ";

        if (i < 0 || i >= certificateChain.length) {
            throw new VEOError(name + "index must be between 0 and getCertificateChainLength()-1");
        }
        b = null;
        try {
            b = certificateChain[i].getEncoded();
        } catch (CertificateEncodingException e) {
            throw new VEOError(name + e.getMessage());
        }
        return b;
    }

    /**
     * Gets the i'th certificate from the certificate chain as an
     * X509Certificate. The first certificate is numbered 0. The method throws a
     * VEOError if the certificate requested is negative, or greater than the
     * number of certificates in the PFX file.
     *
     * @param i index of requested certificate
     * @return an X509Certificate
     * @throws VEOError
     */
    public X509Certificate getX509Certificate(int i) throws VEOError {
        String name = "PFXUser.getX509Certificate(): ";

        if (i < 0 || i >= certificateChain.length) {
            throw new VEOError(name + "index must be between 0 and getCertificateChainLength()-1");
        }
        return (X509Certificate) certificateChain[i];
    }

    /**
     * Return the public and private keys of the signer.
     *
     * @return a KeyPair containing the signer's public and private key.
     */
    public KeyPair getKeyPair() {
        return new KeyPair(pubKey, priKey);
    }

    /**
     * Return the private key of the signer.
     *
     * @return a PrivateKey containing the signer's private key.
     */
    public PrivateKey getPrivate() {
        return priKey;
    }

    /**
     * Return the public key of the signer.
     *
     * @return a PublicKey containing the signer's public key.
     */
    public PublicKey getPublic() {
        return pubKey;
    }
}
