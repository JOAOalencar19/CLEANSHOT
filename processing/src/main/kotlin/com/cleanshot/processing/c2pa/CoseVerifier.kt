package com.cleanshot.processing.c2pa

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.PublicKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec

/**
 * Verifies a COSE_Sign1 signature (RFC 9052) over a detached payload using the leaf certificate
 * carried in the x5chain header. This checks cryptographic consistency only; trust in the
 * certificate (C2PA trust list, revocation, timestamp) is out of scope for offline use.
 */
internal object CoseVerifier {

    data class Parsed(
        val protectedBytes: ByteArray,
        val protectedHeader: Map<Any?, Any?>,
        val unprotectedHeader: Map<Any?, Any?>,
        val signature: ByteArray,
        val alg: Long?,
        val certificates: List<X509Certificate>,
    )

    sealed interface Outcome {
        data object Valid : Outcome
        data object Invalid : Outcome
        data class Unsupported(val reason: String) : Outcome
    }

    fun parse(coseBytes: ByteArray): Parsed {
        val decoded = CborReader.decode(coseBytes)
        val arr = decoded.cborList() ?: error("COSE_Sign1 inválido")
        require(arr.size == 4) { "COSE_Sign1 com ${arr.size} elementos" }
        val prot = arr[0] as? ByteArray ?: error("Cabeçalho protegido ausente")
        val protMap = if (prot.isEmpty()) emptyMap() else CborReader.decode(prot).cborMap() ?: emptyMap()
        val unprot = arr[1].cborMap() ?: emptyMap()
        val sig = arr[3] as? ByteArray ?: error("Assinatura ausente")
        val alg = protMap[1L].cborLong()
        val chain = protMap[33L] ?: protMap["x5chain"] ?: unprot[33L] ?: unprot["x5chain"]
        val ders: List<ByteArray> = when (chain) {
            is ByteArray -> listOf(chain)
            is List<*> -> chain.filterIsInstance<ByteArray>()
            else -> emptyList()
        }
        val cf = CertificateFactory.getInstance("X.509")
        val certs = ders.mapNotNull { runCatching { cf.generateCertificate(ByteArrayInputStream(it)) as X509Certificate }.getOrNull() }
        return Parsed(prot, protMap, unprot, sig, alg, certs)
    }

    fun algName(alg: Long?): String = when (alg) {
        -7L -> "ES256"; -35L -> "ES384"; -36L -> "ES512"
        -37L -> "PS256"; -38L -> "PS384"; -39L -> "PS512"
        -8L -> "EdDSA (Ed25519)"
        null -> "desconhecido"
        else -> "alg $alg"
    }

    fun verify(p: Parsed, payload: ByteArray): Outcome {
        val cert = p.certificates.firstOrNull() ?: return Outcome.Unsupported("Certificado do assinante não encontrado")
        val toBeSigned = CborWriter.sigStructure(p.protectedBytes, payload)
        val key = cert.publicKey
        return try {
            val ok = when (p.alg) {
                -7L -> ecdsa("SHA256withECDSA", key, toBeSigned, p.signature)
                -35L -> ecdsa("SHA384withECDSA", key, toBeSigned, p.signature)
                -36L -> ecdsa("SHA512withECDSA", key, toBeSigned, p.signature)
                -37L -> pss("SHA-256", MGF1ParameterSpec.SHA256, 32, key, toBeSigned, p.signature)
                -38L -> pss("SHA-384", MGF1ParameterSpec.SHA384, 48, key, toBeSigned, p.signature)
                -39L -> pss("SHA-512", MGF1ParameterSpec.SHA512, 64, key, toBeSigned, p.signature)
                -8L -> Signature.getInstance("Ed25519").run { initVerify(key); update(toBeSigned); verify(p.signature) }
                else -> return Outcome.Unsupported("Algoritmo ${algName(p.alg)} não suportado")
            }
            if (ok) Outcome.Valid else Outcome.Invalid
        } catch (e: java.security.NoSuchAlgorithmException) {
            Outcome.Unsupported("Algoritmo ${algName(p.alg)} indisponível neste dispositivo")
        } catch (e: java.security.InvalidKeyException) {
            Outcome.Unsupported("Chave do certificado incompatível: ${e.message}")
        } catch (e: java.security.SignatureException) {
            Outcome.Invalid
        }
    }

    private fun ecdsa(alg: String, key: PublicKey, data: ByteArray, raw: ByteArray): Boolean {
        val der = rawToDer(raw) ?: return false
        return Signature.getInstance(alg).run { initVerify(key); update(data); verify(der) }
    }

    private fun pss(hash: String, mgf: MGF1ParameterSpec, salt: Int, key: PublicKey, data: ByteArray, sig: ByteArray): Boolean {
        val spec = PSSParameterSpec(hash, "MGF1", mgf, salt, 1)
        val s = try {
            Signature.getInstance("RSASSA-PSS").apply { setParameter(spec) }
        } catch (_: Exception) {
            // Android (Conscrypt) naming.
            Signature.getInstance(hash.replace("-", "") + "withRSA/PSS").apply { setParameter(spec) }
        }
        s.initVerify(key); s.update(data)
        return s.verify(sig)
    }

    /** COSE ECDSA signatures are r||s; the JCA expects ASN.1 DER. */
    private fun rawToDer(raw: ByteArray): ByteArray? {
        if (raw.isEmpty() || raw.size % 2 != 0) return null
        val half = raw.size / 2
        fun intBytes(b: ByteArray): ByteArray = BigInteger(1, b).toByteArray()
        val r = intBytes(raw.copyOfRange(0, half))
        val s = intBytes(raw.copyOfRange(half, raw.size))
        val seq = ByteArrayOutputStream()
        fun writeLen(out: ByteArrayOutputStream, len: Int) {
            if (len < 0x80) out.write(len) else if (len < 0x100) { out.write(0x81); out.write(len) } else { out.write(0x82); out.write(len shr 8); out.write(len and 0xFF) }
        }
        seq.write(0x02); writeLen(seq, r.size); seq.write(r)
        seq.write(0x02); writeLen(seq, s.size); seq.write(s)
        val body = seq.toByteArray()
        val out = ByteArrayOutputStream()
        out.write(0x30); writeLen(out, body.size); out.write(body)
        return out.toByteArray()
    }
}
