package net.postchain.zkp.plonk

import net.postchain.common.data.KECCAK256
import net.postchain.zkp.curve.Curve
import net.postchain.zkp.curve.G1Point
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.math.BigInteger
import java.security.MessageDigest
import java.security.Security

/**
 * A class for generating challenges using Keccak-256 hashing
 */
class Keccak256Transcript(private val curve: Curve) {
    private val digest: MessageDigest

    init {
        // We add this provider so that we can get keccak-256 message digest instances
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }

        digest = MessageDigest.getInstance(KECCAK256)
    }

    fun reset() {
        digest.reset()
    }

    fun addScalar(scalar: BigInteger) {
        digest.update(curve.encodeScalar(scalar))
    }

    fun addPolCommitment(point: G1Point) {
        digest.update(curve.encodePoint(point))
    }

    fun getChallenge(): BigInteger {
        // Hash the buffer and convert to BigInteger
        val hash = digest.digest()
        return BigInteger(1, hash).mod(curve.order)
    }
}
