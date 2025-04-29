package net.postchain.zkp.plonk

import net.postchain.common.exception.UserMistake
import net.postchain.zkp.curve.Curve
import net.postchain.zkp.curve.G1Point
import java.math.BigInteger

object PlonkVerifier {

    /**
     * Holder of all computed challenges
     */
    @Suppress("ArrayInDataClass")
    private data class CalculatedChallenges(
            val alpha: BigInteger,
            val alpha2: BigInteger,
            val beta: BigInteger,
            val gamma: BigInteger,
            val xi: BigInteger,
            val xin: BigInteger,
            val betaXi: BigInteger,
            val v: Array<BigInteger>,
            val u: BigInteger,
            val zh: BigInteger,
    )

    /**
     * Verifies the validity of a PLONK proof using the provided verification key and public signals.
     *
     * @param vk The verification key containing curve details, constraints, and commitment points.
     * @param proof The proof to be verified, including commitments and evaluated values.
     * @param pubSignals The array of public signals that are part of the proof verification process.
     * @throws UserMistake if the proof is invalid with an appropriate error message.
     */
    fun verifyProof(vk: PlonkVerificationKey, proof: PlonkProof, pubSignals: Array<BigInteger>) {
        checkProofData(vk.curve, proof, pubSignals)

        // Check if the number of public signals matches the verification key
        if (pubSignals.size != vk.nPublic) {
            throw UserMistake("Number of public signals does not match verification key")
        }

        val calculatedChallenges = calculateChallenges(proof, pubSignals, vk)

        val lagrangePolynomials = calculateLagrangePolynomials(calculatedChallenges, vk)

        val pi = calculatePublicInputContribution(lagrangePolynomials, pubSignals, vk)

        val r0 = calculateR0(vk.curve, calculatedChallenges, lagrangePolynomials, pi, proof)

        val d = calculateD(calculatedChallenges, lagrangePolynomials, proof, vk)

        val e = calculateE(vk.curve, calculatedChallenges, r0, proof)

        val f = calculateF(calculatedChallenges, d, proof, vk)

        checkPairing(e, f, proof, calculatedChallenges, vk)
    }

    private fun checkProofData(curve: Curve, proof: PlonkProof, pubSignals: Array<BigInteger>) {
        // Check that proof commitments belong to the curve and are in the field
        if (!checkIsValidPoint(curve, proof.A)) throw UserMistake("Proof commitment A is not a valid point on the curve")
        if (!checkIsValidPoint(curve, proof.B)) throw UserMistake("Proof commitment B is not a valid point on the curve")
        if (!checkIsValidPoint(curve, proof.C)) throw UserMistake("Proof commitment C is not a valid point on the curve")
        if (!checkIsValidPoint(curve, proof.Z)) throw UserMistake("Proof commitment Z is not a valid point on the curve")
        if (!checkIsValidPoint(curve, proof.T1)) throw UserMistake("Proof commitment T1 is not a valid point on the curve")
        if (!checkIsValidPoint(curve, proof.T2)) throw UserMistake("Proof commitment T2 is not a valid point on the curve")
        if (!checkIsValidPoint(curve, proof.T3)) throw UserMistake("Proof commitment T3 is not a valid point on the curve")
        if (!checkIsValidPoint(curve, proof.Wxi)) throw UserMistake("Proof commitment Wxi is not a valid point on the curve")
        if (!checkIsValidPoint(curve, proof.Wxiw)) throw UserMistake("Proof commitment Wxiw is not a valid point on the curve")

        // Check proof evaluations are in the field
        if (!checkIsInField(curve, proof.evalA)) throw UserMistake("Proof evaluation eval A is not in the field")
        if (!checkIsInField(curve, proof.evalB)) throw UserMistake("Proof evaluation eval B is not in the field")
        if (!checkIsInField(curve, proof.evalC)) throw UserMistake("Proof evaluation eval C is not in the field")
        if (!checkIsInField(curve, proof.evalS1)) throw UserMistake("Proof evaluation eval S1 is not in the field")
        if (!checkIsInField(curve, proof.evalS2)) throw UserMistake("Proof evaluation eval S2 is not in the field")
        if (!checkIsInField(curve, proof.evalZw)) throw UserMistake("Proof evaluation eval Zw is not in the field")

        // Check public signals are in the field
        if (!pubSignals.all { checkIsInField(curve, it) }) throw UserMistake("One or more public signals are not in the field")
    }

    private fun checkIsValidPoint(curve: Curve, p: G1Point) =
            curve.isPointOnCurve(p) && checkIsInField(curve, p.x) && checkIsInField(curve, p.y)

    private fun checkIsInField(curve: Curve, v: BigInteger) = v >= BigInteger.ZERO && v < curve.fieldModulus

    /**
     * Calculates the challenges required for verifying a PLONK proof.
     *
     * @param proof A PLONK proof containing commitments and evaluation points.
     * @param pubSignals An array of public signals used during verification.
     * @param vk The verification key containing curve details and commitment points.
     * @return A `CalculatedChallenges` object containing all computed challenges.
     */
    private fun calculateChallenges(proof: PlonkProof, pubSignals: Array<BigInteger>, vk: PlonkVerificationKey): CalculatedChallenges {
        val curve = vk.curve
        val transcript = Keccak256Transcript(curve)

        val beta = calculateBeta(transcript, proof, pubSignals, vk)

        val gamma = calculateGamma(transcript, beta)

        val alpha = calculateAlpha(transcript, beta, gamma, proof)
        val alpha2 = alpha * alpha % curve.order

        val xi = calculateXi(transcript, alpha, proof)

        val v1 = calculateV1(transcript, xi, proof)

        val betaXi = beta * xi % curve.order

        // challenges.xi^n
        var xin = xi
        // Calculate xi^n where n = 2^power
        repeat(vk.power) {
            xin = xin * xin % curve.order
        }

        val zh = (xin - BigInteger.ONE) % curve.order

        val v2 = v1 * v1 % curve.order
        val v3 = v2 * v1 % curve.order
        val v4 = v3 * v1 % curve.order
        val v5 = v4 * v1 % curve.order

        val u = calculateU(transcript, proof)

        return CalculatedChallenges(
                alpha,
                alpha2,
                beta,
                gamma,
                xi,
                xin,
                betaXi,
                arrayOf(BigInteger.ZERO, v1, v2, v3, v4, v5),
                u,
                zh
        )
    }

    private fun calculateBeta(transcript: Keccak256Transcript, proof: PlonkProof, pubSignals: Array<BigInteger>, vk: PlonkVerificationKey): BigInteger {
        transcript.reset()

        // Add polynomial commitments
        transcript.addPolCommitment(vk.Qm)
        transcript.addPolCommitment(vk.Ql)
        transcript.addPolCommitment(vk.Qr)
        transcript.addPolCommitment(vk.Qo)
        transcript.addPolCommitment(vk.Qc)
        transcript.addPolCommitment(vk.S1)
        transcript.addPolCommitment(vk.S2)
        transcript.addPolCommitment(vk.S3)

        // Add public signals
        for (i in pubSignals.indices) {
            transcript.addScalar(pubSignals[i])
        }

        // Add proof commitments
        transcript.addPolCommitment(proof.A)
        transcript.addPolCommitment(proof.B)
        transcript.addPolCommitment(proof.C)

        return transcript.getChallenge()
    }

    private fun calculateGamma(transcript: Keccak256Transcript, beta: BigInteger): BigInteger {
        transcript.reset()
        transcript.addScalar(beta)
        return transcript.getChallenge()
    }

    private fun calculateAlpha(transcript: Keccak256Transcript, beta: BigInteger, gamma: BigInteger, proof: PlonkProof): BigInteger {
        transcript.reset()
        transcript.addScalar(beta)
        transcript.addScalar(gamma)
        transcript.addPolCommitment(proof.Z)
        return transcript.getChallenge()
    }

    private fun calculateXi(transcript: Keccak256Transcript, alpha: BigInteger, proof: PlonkProof): BigInteger {
        transcript.reset()
        transcript.addScalar(alpha)
        transcript.addPolCommitment(proof.T1)
        transcript.addPolCommitment(proof.T2)
        transcript.addPolCommitment(proof.T3)
        return transcript.getChallenge()
    }

    private fun calculateV1(transcript: Keccak256Transcript, xi: BigInteger, proof: PlonkProof): BigInteger {
        transcript.reset()
        transcript.addScalar(xi)
        transcript.addScalar(proof.evalA)
        transcript.addScalar(proof.evalB)
        transcript.addScalar(proof.evalC)
        transcript.addScalar(proof.evalS1)
        transcript.addScalar(proof.evalS2)
        transcript.addScalar(proof.evalZw)
        return transcript.getChallenge()
    }

    private fun calculateU(transcript: Keccak256Transcript, proof: PlonkProof): BigInteger {
        transcript.reset()
        transcript.addPolCommitment(proof.Wxi)
        transcript.addPolCommitment(proof.Wxiw)
        return transcript.getChallenge()
    }

    /**
     * Calculates Lagrange basis polynomials evaluated at a specific point needed for the verification.
     * These polynomials are used in the pairing-based verification equation.
     *
     * @param challenges The calculated challenge values used in the ZK proof verification
     * @param vk The verification key containing curve parameters and other constants
     * @return Array of Lagrange polynomial evaluations at the challenge point
     */
    private fun calculateLagrangePolynomials(challenges: CalculatedChallenges, vk: PlonkVerificationKey): Array<BigInteger> {
        val curve = vk.curve
        val curveOrder = curve.order

        // Get root of unity at the correct power level for this verification key
        val rootOfUnity = curve.rootsOfUnity[vk.power]

        // Number of Lagrange polynomials to calculate
        val numPolynomials = vk.nLagrange

        val lagrangeValues = ArrayList<BigInteger>()

        // Start with omega^0 = 1
        var omegaPower = BigInteger.ONE

        // Calculate Lagrange polynomial evaluations for each point
        repeat(numPolynomials) { i ->
            // Calculate numerator: n * (xi - omega^i) mod q
            val numerator = vk.n * ((challenges.xi - omegaPower) % curveOrder) % curveOrder

            // Calculate zh * omega^i mod q (part of the formula)
            val zhTimesOmegaPower = omegaPower * challenges.zh % curveOrder

            // Combine numerator and denominator: (zh * omega^i) * (n * (xi - omega^i))^(-1) mod q
            val lagrangeValue = zhTimesOmegaPower * numerator.modInverse(curveOrder) % curveOrder

            // Store this Lagrange value
            lagrangeValues.add(lagrangeValue)

            // Update omega^i to omega^(i+1) for next iteration
            omegaPower = omegaPower * rootOfUnity % curveOrder
        }

        return lagrangeValues.toTypedArray()
    }

    /**
     * Calculates the public input contribution to the verification equation.
     *
     * This function computes a scalar value that represents the combined effect of all public inputs
     * in the zero-knowledge proof verification. It does this by taking a weighted sum of public inputs,
     * using the Lagrange basis polynomials as weights.
     *
     * @param lagrangePolynomials The precomputed Lagrange polynomial evaluations
     * @param publicInputs The array of public inputs (signals) to the circuit
     * @param vk The verification key containing circuit parameters
     * @return A scalar value representing the public input contribution
     */
    private fun calculatePublicInputContribution(
            lagrangePolynomials: Array<BigInteger>,
            publicInputs: Array<BigInteger>,
            vk: PlonkVerificationKey
    ): BigInteger {
        val curveOrder = vk.curve.order
        var contribution = BigInteger.ZERO

        // Calculate the weighted sum of public inputs using Lagrange polynomials
        // For each public input position in the verification key
        for (i in 0 until vk.nPublic) {
            if (i < publicInputs.size) {
                // Multiply public input by its corresponding Lagrange polynomial evaluation
                val weightedInput = lagrangePolynomials[i] * publicInputs[i] % curveOrder

                // Subtract from the accumulated sum (note: this is a negative contribution by convention)
                contribution = (contribution - weightedInput) % curveOrder
            }
        }

        // Ensure the result is in the proper range [0, curveOrder-1]
        if (contribution < BigInteger.ZERO) {
            contribution += curveOrder
        }

        return contribution
    }

    /**
     * Calculates the r0 component of the PLONK verification equation. T
     *
     * This function computes a scalar value that represents the public input contribution and constraint relations
     * adjustment in the verification equation.
     *
     * @param curve The elliptic curve providing finite field operations
     * @param challenges The calculated challenge values derived during verification
     * @param lagrangePolynomials The precomputed Lagrange polynomial evaluations
     * @param publicInputContribution The calculated contribution from public inputs
     * @param proof The proof object containing polynomial evaluations
     * @return The r0 component of the verification equation
     */
    private fun calculateR0(
            curve: Curve,
            challenges: CalculatedChallenges,
            lagrangePolynomials: Array<BigInteger>,
            publicInputContribution: BigInteger,
            proof: PlonkProof
    ): BigInteger {
        val curveOrder = curve.order

        // First term: public input contribution
        val publicTerm = publicInputContribution

        // Second term: Lagrange polynomial L_0 multiplied by alpha²
        val lagrangeTerm = lagrangePolynomials[0] * challenges.alpha2 % curveOrder

        // Third term components:
        // Calculate (evalA + β·evalS1 + γ)
        val factorA = (proof.evalA + (challenges.beta * proof.evalS1 % curveOrder) + challenges.gamma) % curveOrder

        // Calculate (evalB + β·evalS2 + γ)
        val factorB = (proof.evalB + (challenges.beta * proof.evalS2 % curveOrder) + challenges.gamma) % curveOrder

        // Calculate (evalC + γ)
        val factorC = (proof.evalC + challenges.gamma) % curveOrder

        // Combine the three factors, multiply by evalZw and alpha
        var copyConstraintTerm = factorA * factorB % curveOrder * factorC % curveOrder
        copyConstraintTerm = copyConstraintTerm * proof.evalZw % curveOrder
        copyConstraintTerm = copyConstraintTerm * challenges.alpha % curveOrder

        // Final combination: publicTerm - lagrangeTerm - copyConstraintTerm (all modulo curveOrder)
        // We use (curveOrder - term) to represent subtraction in modular arithmetic
        val result = (publicTerm + (curveOrder - lagrangeTerm) % curveOrder +
                (curveOrder - copyConstraintTerm) % curveOrder) % curveOrder

        return result
    }

    /**
     * Calculates the D component of the PLONK verification equation.
     *
     * This function computes a G1 curve point that represents the left-hand side of the pairing-based
     * verification equation.
     *
     * @param challenges The calculated challenge values for verification
     * @param lagrangePolynomials The precomputed Lagrange polynomial evaluations
     * @param proof The PLONK proof containing polynomial evaluations and commitments
     * @param vk The verification key containing circuit parameters
     * @return A G1 curve point representing the left-hand side of the verification equation
     */
    private fun calculateD(
            challenges: CalculatedChallenges,
            lagrangePolynomials: Array<BigInteger>,
            proof: PlonkProof,
            vk: PlonkVerificationKey
    ): G1Point {
        val curve = vk.curve
        val curveOrder = curve.order

        // ===== PART 1: Gate constraint evaluation =====
        // Compute Qc + Qm*a*b + Ql*a + Qr*b + Qo*c

        // Calculate a*b for the multiplication constraint
        val abProduct = proof.evalA * proof.evalB % curveOrder
        val qmScaledByAB = curve.multiplyPoint(vk.Qm, abProduct)

        // Start with Qc + Qm*a*b
        var leftHandSide = curve.addPoints(vk.Qc, qmScaledByAB)

        // Add Ql*a (linear constraint on wire a)
        val qlScaledByA = curve.multiplyPoint(vk.Ql, proof.evalA)
        leftHandSide = curve.addPoints(leftHandSide, qlScaledByA)

        // Add Qr*b (linear constraint on wire b)
        val qrScaledByB = curve.multiplyPoint(vk.Qr, proof.evalB)
        leftHandSide = curve.addPoints(leftHandSide, qrScaledByB)

        // Add Qo*c (linear constraint on wire c - output wire)
        val qoScaledByC = curve.multiplyPoint(vk.Qo, proof.evalC)
        leftHandSide = curve.addPoints(leftHandSide, qoScaledByC)

        // ===== PART 2: Permutation argument =====
        // Calculate factors for the permutation check
        val betaXi = challenges.betaXi
        val gamma = challenges.gamma

        // Calculate (a + βξ + γ)
        val factorA = (proof.evalA + betaXi + gamma) % curveOrder

        // Calculate (b + βξk₁ + γ)
        val factorB = (proof.evalB + (betaXi * vk.k1 % curveOrder) + gamma) % curveOrder

        // Calculate (c + βξk₂ + γ)
        val factorC = (proof.evalC + (betaXi * vk.k2 % curveOrder) + gamma) % curveOrder

        // Compute α·(a + βξ + γ)·(b + βξk₁ + γ)·(c + βξk₂ + γ)
        val permutationTerm1 = factorA * factorB % curveOrder * factorC % curveOrder * challenges.alpha % curveOrder

        // Compute α²·L₀(ξ) (L₀ is the first Lagrange polynomial)
        val permutationTerm2 = lagrangePolynomials[0] * challenges.alpha2 % curveOrder

        // Calculate Z·(permutationTerm1 + permutationTerm2 + u)
        val permutationPoint = curve.multiplyPoint(
                proof.Z,
                (permutationTerm1 + permutationTerm2 + challenges.u) % curveOrder
        )

        // ===== PART 3: Copy constraint check =====
        // Calculate (a + β·s₁ + γ)
        val copyFactorA = (proof.evalA + (challenges.beta * proof.evalS1 % curveOrder) + gamma) % curveOrder

        // Calculate (b + β·s₂ + γ)
        val copyFactorB = (proof.evalB + (challenges.beta * proof.evalS2 % curveOrder) + gamma) % curveOrder

        // Calculate α·β·zω
        val copyFactorC = challenges.alpha * challenges.beta % curveOrder * proof.evalZw % curveOrder

        // Compute S₃·(copyFactorA·copyFactorB·copyFactorC)
        val copyConstraintPoint = curve.multiplyPoint(
                vk.S3,
                copyFactorA * copyFactorB % curveOrder * copyFactorC % curveOrder
        )

        // ===== PART 4: Linearization and quotient polynomial check =====
        // Calculate ξⁿ and ξ²ⁿ for combining the T polynomials
        val xiPowerN = challenges.xin
        val xiPowerN2 = xiPowerN * xiPowerN % curveOrder

        // Combine T₁ + ξⁿ·T₂ + ξ²ⁿ·T₃
        var quotientPoint = curve.addPoints(proof.T1, curve.multiplyPoint(proof.T2, xiPowerN))
        quotientPoint = curve.addPoints(quotientPoint, curve.multiplyPoint(proof.T3, xiPowerN2))

        // Multiply by the vanishing polynomial evaluated at ξ
        quotientPoint = curve.multiplyPoint(quotientPoint, challenges.zh)

        // ===== COMBINE ALL PARTS =====
        // For the copy constraint and quotient parts, we need to negate the points
        // (subtract rather than add in the group operation)
        val copyConstraintNegated = G1Point(
                copyConstraintPoint.x,
                (curve.fieldModulus - copyConstraintPoint.y) % curve.fieldModulus
        )

        val quotientPointNegated = G1Point(
                quotientPoint.x,
                (curve.fieldModulus - quotientPoint.y) % curve.fieldModulus
        )

        // Add all components to form the final left-hand side
        leftHandSide = curve.addPoints(leftHandSide, permutationPoint)
        leftHandSide = curve.addPoints(leftHandSide, copyConstraintNegated)
        return curve.addPoints(leftHandSide, quotientPointNegated)
    }

    /**
     * Calculates the E point for the PLONK verification equation.
     *
     * This function computes a G1 curve point that represents part of the right-hand side of the
     * pairing-based verification equation.
     *
     * @param curve The elliptic curve providing finite field operations
     * @param challenges The calculated challenge values used for verification
     * @param r0 The r0 component calculated earlier in the verification process
     * @param proof The PLONK proof containing polynomial evaluations
     * @return A G1 curve point for the pairing-based verification equation
     */
    private fun calculateE(
            curve: Curve,
            challenges: CalculatedChallenges,
            r0: BigInteger,
            proof: PlonkProof
    ): G1Point {
        val curveOrder = curve.order
        val challengeFactors = challenges.v

        // Calculate the scalar value as a linear combination:
        // scalar = -r0 + evalA·v₁ + evalB·v₂ + evalC·v₃ + evalS1·v₄ + evalS2·v₅ + evalZw·u

        // Start with -r0 (negative r0)
        var scalar = (curveOrder - r0) % curveOrder

        // Add evalA·v₁ (wire A evaluation scaled by challenge v₁)
        scalar = (scalar + (proof.evalA * challengeFactors[1] % curveOrder)) % curveOrder

        // Add evalB·v₂ (wire B evaluation scaled by challenge v₂)
        scalar = (scalar + (proof.evalB * challengeFactors[2] % curveOrder)) % curveOrder

        // Add evalC·v₃ (wire C evaluation scaled by challenge v₃)
        scalar = (scalar + (proof.evalC * challengeFactors[3] % curveOrder)) % curveOrder

        // Add evalS1·v₄ (permutation S1 evaluation scaled by challenge v₄)
        scalar = (scalar + (proof.evalS1 * challengeFactors[4] % curveOrder)) % curveOrder

        // Add evalS2·v₅ (permutation S2 evaluation scaled by challenge v₅)
        scalar = (scalar + (proof.evalS2 * challengeFactors[5] % curveOrder)) % curveOrder

        // Add evalZw·u (Z polynomial at ω scaled by challenge u)
        scalar = (scalar + (proof.evalZw * challenges.u % curveOrder)) % curveOrder

        // Compute E = G₁ * scalar (multiply G1 generator by the computed scalar)
        return curve.multiplyPoint(curve.generatorG1, scalar)
    }

    /**
     * Calculates the final combined G1 point for the PLONK verification equation.
     *
     * This function computes the right-hand side of the pairing-based verification equation.
     *
     * @param challenges The calculated challenge values used for verification
     * @param d The previously calculated left-hand side point (D)
     * @param proof The PLONK proof containing polynomial commitments
     * @param vk The verification key containing circuit parameters
     * @return The final combined G1 point for the pairing-based verification equation
     */
    private fun calculateF(
            challenges: CalculatedChallenges,
            d: G1Point,
            proof: PlonkProof,
            vk: PlonkVerificationKey
    ): G1Point {
        val curve = vk.curve
        val challengeFactors = challenges.v

        // Start with the left-hand side point (D)
        // Then add each commitment scaled by its corresponding challenge factor

        // Add wire polynomial commitment A scaled by v1
        // F = D + A·v₁
        var combinedPoint = curve.addPoints(
                d,
                curve.multiplyPoint(proof.A, challengeFactors[1])
        )

        // Add wire polynomial commitment B scaled by v2
        // F = F + B·v₂
        combinedPoint = curve.addPoints(
                combinedPoint,
                curve.multiplyPoint(proof.B, challengeFactors[2])
        )

        // Add wire polynomial commitment C scaled by v3
        // F = F + C·v₃
        combinedPoint = curve.addPoints(
                combinedPoint,
                curve.multiplyPoint(proof.C, challengeFactors[3])
        )

        // Add permutation commitment S1 scaled by v4
        // F = F + S₁·v₄
        combinedPoint = curve.addPoints(
                combinedPoint,
                curve.multiplyPoint(vk.S1, challengeFactors[4])
        )

        // Add permutation commitment S2 scaled by v5
        // F = F + S₂·v₅
        return curve.addPoints(
                combinedPoint,
                curve.multiplyPoint(vk.S2, challengeFactors[5])
        )
    }

    /**
     * Performs the final pairing-based verification check for the PLONK proof.
     *
     * The pairing check verifies that the prover knows the correct polynomial evaluations
     * at the challenge points without revealing the actual polynomials.
     *
     * @param e The E point calculated earlier in verification (scaled G1 generator)
     * @param f The F point calculated earlier in verification (combined commitments)
     * @param proof The PLONK proof containing polynomial openings
     * @param challenges The calculated challenge values used throughout verification
     * @param vk The verification key containing circuit parameters
     * @return True if the pairing equality holds, indicating a valid proof
     * @throws UserMistake if the pairing check fails, indicating an invalid proof
     */
    private fun checkPairing(
            e: G1Point,
            f: G1Point,
            proof: PlonkProof,
            challenges: CalculatedChallenges,
            vk: PlonkVerificationKey
    ) {
        val curve = vk.curve
        val curveOrder = curve.order

        // ===== STEP 1: Calculate the first point for the pairing check =====
        // A₁ = W_ξ + W_ξω · u
        // This combines the opening proof at ξ and ξω
        val openingCombination = curve.addPoints(
                proof.Wxi,  // Opening proof at challenge point ξ
                curve.multiplyPoint(proof.Wxiw, challenges.u)  // Opening proof at ξω scaled by u
        )

        // ===== STEP 2: Calculate the second point for the pairing check =====
        // Start with W_ξ · ξ
        var rightSidePoint = curve.multiplyPoint(proof.Wxi, challenges.xi)

        // Add W_ξω · (u · ξ · ω^n)
        // This factor represents the shifted challenge point
        val shiftedChallengeScalar = challenges.u * challenges.xi % curveOrder * curve.rootsOfUnity[vk.power] % curveOrder
        rightSidePoint = curve.addPoints(
                rightSidePoint,
                curve.multiplyPoint(proof.Wxiw, shiftedChallengeScalar)
        )

        // Add the combined point F
        rightSidePoint = curve.addPoints(rightSidePoint, f)

        // Subtract the generator point E (using point negation)
        val negatedGeneratorPoint = G1Point(
                e.x,
                (curve.fieldModulus - e.y) % curve.fieldModulus
        )
        rightSidePoint = curve.addPoints(rightSidePoint, negatedGeneratorPoint)

        // ===== STEP 3: Negate the first point for the pairing equation =====
        // For the pairing equation e(-A₁, X₂) = e(B₁, G₂), we need -A₁
        val negatedOpeningCombination = G1Point(
                openingCombination.x,
                (curve.fieldModulus - openingCombination.y) % curve.fieldModulus
        )

        // ===== STEP 4: Check the pairing equation =====
        // The verification succeeds if e(-A₁, X₂) = e(B₁, G₂)
        val pairingResult = curve.verifyPairingEquality(listOf(
                negatedOpeningCombination to vk.X2,  // Left side of the pairing equation
                rightSidePoint to curve.generatorG2   // Right side of the pairing equation
        ))

        if (!pairingResult) {
            throw UserMistake("Pairing check failed: proof is invalid")
        }
    }
}
