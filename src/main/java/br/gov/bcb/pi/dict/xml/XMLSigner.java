package br.gov.bcb.pi.dict.xml;

import com.google.common.base.Preconditions;
import lombok.SneakyThrows;
import org.w3c.dom.Node;

import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.keyinfo.X509IssuerSerial;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static javax.xml.crypto.dsig.CanonicalizationMethod.EXCLUSIVE;

class XMLSigner {

    private static final String KEY_INFO_ID = "key-info-id";
    private static final String ROOT_URI = "";
    private static final String KEY_STORE_TYPE = "PKCS12";
    private final XMLSignatureFactory xmlSignatureFactory = XMLSignatureFactory.getInstance("DOM");

    private final KeyStore store;
    private final String password;

    XMLSigner(String keyStoreFile, String password) {
        this.password = password;
        try {
            InputStream keystoreIS = new FileInputStream(keyStoreFile);
            store = KeyStore.getInstance(KEY_STORE_TYPE);
            store.load(keystoreIS, this.password.toCharArray());
            Preconditions.checkArgument(store.size() == 1, "Esperado que keystore tivesse apenas 1 alias");
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    void sign(Node node) {
        try {
            XMLSignature xmlSignature = newXMLSignature(newArrayList(newReferenceKeyInfo(), newReferenceRoot()));
            DOMSignContext signContext = newDOMSignContext(node);
            xmlSignature.sign(signContext);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao assinar XML", e);
        }
    }

    private DOMSignContext newSignContext(Node node, PrivateKey privateKey) {
        // No DICT, elemento 'Signature' é sempre colocado como filho da tag raiz
        DOMSignContext dsc = new DOMSignContext(privateKey, node.getFirstChild(), node.getFirstChild().getFirstChild());
        dsc.setDefaultNamespacePrefix("ds");
        return dsc;
    }

    private Reference newReferenceRoot() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        return xmlSignatureFactory.newReference(
                ROOT_URI,
                xmlSignatureFactory.newDigestMethod(DigestMethod.SHA256, null),
                Arrays.asList(
                        xmlSignatureFactory.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null),
                        xmlSignatureFactory.newCanonicalizationMethod(EXCLUSIVE, (C14NMethodParameterSpec) null)
                ),
                null,
                null
        );
    }


    private DOMSignContext newSignContext(Node node) {
        return newSignContext(node, getPrivateKey());
    }

    private Reference newReferenceKeyInfo() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        return xmlSignatureFactory.newReference(
                "#" + KEY_INFO_ID,
                xmlSignatureFactory.newDigestMethod(DigestMethod.SHA256, null),
                Collections.singletonList(xmlSignatureFactory.newCanonicalizationMethod(EXCLUSIVE, (C14NMethodParameterSpec) null)),
                null,
                null
        );
    }

    private XMLSignature newXMLSignature(List<Reference> referenceList) throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        KeyInfoFactory kif = xmlSignatureFactory.getKeyInfoFactory();
        KeyInfo keyInfo = kif.newKeyInfo(Collections.singletonList(getX509Data(kif)), KEY_INFO_ID);

        return xmlSignatureFactory.newXMLSignature(
                newSignedInfo(referenceList),
                keyInfo,
                null,
                null,
                null
        );
    }

    private X509Data newX509Data(KeyInfoFactory kif, X509Certificate certificate) {
        X509IssuerSerial x509IssuerSerial = kif.newX509IssuerSerial(certificate.getIssuerDN().toString(), certificate.getSerialNumber());
        return kif.newX509Data(Collections.singletonList(x509IssuerSerial));
    }

    private X509Data getX509Data(KeyInfoFactory kif) {
        return newX509Data(kif, getCertificate());
    }

    private SignedInfo newSignedInfo(List<Reference> referenceList) throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        return xmlSignatureFactory.newSignedInfo(
                xmlSignatureFactory.newCanonicalizationMethod(EXCLUSIVE, (C14NMethodParameterSpec) null),
                xmlSignatureFactory.newSignatureMethod("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256", null),
                referenceList
        );
    }

    private DOMSignContext newDOMSignContext(Node node) {
        return newSignContext(node);
    }

    @SneakyThrows
    private X509Certificate getCertificate() {
        String alias = store.aliases().nextElement();
        return (X509Certificate) store.getCertificate(alias);
    }

    @SneakyThrows
    private PrivateKey getPrivateKey() {
        String alias = store.aliases().nextElement();
        return (PrivateKey) store.getKey(alias, this.password.toCharArray());
    }

}
