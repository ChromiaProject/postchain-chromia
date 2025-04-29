# Guide to PLONK verification with ZKPGTXModule

## Background

PLONK (Permutations over Lagrange-bases for Oecumenical Noninteractive arguments of Knowledge) is a zero-knowledge proof
system that allows one party to prove to another that they have knowledge of certain information without revealing the
information itself. PLONK is known for its efficient verification process and universal trusted setup, making it
particularly suitable for blockchain applications. It improves upon previous ZK-SNARK systems by requiring only one
universal trusted setup that can be used for all circuits of a given size.

Original paper: https://eprint.iacr.org/2019/953.pdf

## Blockchain configuration

To be able to verify PLONK proofs, you need to add `ZKPGTXModule` to your blockchain configuration.
In addition, you also need to add your verification key(s) to it. Since it's possible to have more than one key,
you need to give them an identifier in the configuration, see an example below:

```yaml
gtx:
  modules:
    - "net.postchain.zkp.ZKPGTXModule"
zkp:
  plonk:
    verification_keys:
      test_vk:
        Qc:
          x: 0L
          y: 0L
        Ql:
          x: 14834510898339141169329695685511700666258129880115403401229217490569515343302L
          y: 9464224989088393801563875716232716915352580613417007162533827452727295446934L
        Qm:
          x: 14319141532948637259101778790582856386713203034743319527668731828185741400626L
          y: 18803411281712541135482987792568462445904566200816418568722397188523035189143L
        Qo:
          x: 21364495085187694205438833340386123229650091635008748320283906080029143641039L
          y: 20444368598332986012476714370015271579755130392073789088854120974413040917081L
        Qr:
          x: 16351212189779639003908022196613349497559341482069657547941244577951374625350L
          y: 4031150659123603989265639197462571629326313607153532089054168665871713116509L
        S1:
          x: 18126157909848214547544885505634038550836716698610211113963616409746950617323L
          y: 9480441132510474920656224855370744169816505579366946377741851539331522216404L
        S2:
          x: 15312771180064260077439958970242428939484644647049778052207315835174955686500L
          y: 3394171162668419319900753190450314872629638145244728026210508800204487774112L
        S3:
          x: 15657500937529974074969620066780498543702080929706007838285907462589433927278L
          y: 10353909101117571868937190939592663817839933444432483255231136649076311981690L
        X_2:
          x1: 19518502430870438181592443054401419581386850463392492809261261189226441340111L
          x2: 8184812567585147824016587988320202893865389288562095514010129273046904740147L
          y1: 10435902743221815611441838668065138241026433470816816910474433321658510434042L
          y2: 3114164934987634673554850993955208553008076750558125585683111415888626769753L
        curve: bn128
        k1: 2L
        k2: 3L
        nPublic: 1
        power: 11
```

As you can see, you also specify which curve that should be used in the key. Currently, the following values are
allowed:

- `bn128`
- `bls12381`

Matching the currently supported curves `BN128` and `BLS12-381`.

## Rell library

There is a small Rell library that you can install to help you check proofs:

```yaml
zkp:
  registry: https://gitlab.com/chromaway/postchain-chromia.git
  path: chromia-infrastructure/rell/src/lib/zkp
  rid: x"8934250CED0D8C7FB46C458CDB303236AA70F3666DA67376E75E89D16F125FF9"
```

It exposes the following functions that you can use to verify that a proof is present in current tx:

```rell
/**
 * Checks whether or not the current transaction contains a valid PLONK proof.
 *
 * @param verification_key_id ID of the verification key that the proof must have been validated with
 * @param public_signals The public signals that the proof must have been validated with
 */
function check_plonk_proof(
    verification_key_id: text,
    public_signals: list<big_integer>
)

/**
 * Checks whether or not the current transaction contains a valid PLONK proof operation before current operation.
 *
 * @param verification_key_id ID of the verification key that the proof must have been validated with
 * @return The public signals of the preceding proof operation
 */
function extract_signals_from_preceeding_proof_op(verification_key_id: text): list<big_integer>
```

## Submitting a proof from a client

To add a proof to a transaction, you need to add an operation with name `zkp_plonk_verify` with the following
arguments:

```
[
    GtvString, // verification key ID
    PlonkProof, // Proof itself, see details below
    GtvArray<GtvBigInteger> // Public signals
]
```

So what does `PlonkProof` look like, it is a `GtvArray` with the following structure:

```
[
    GtvArray<GtvBigInteger>, // A [x, y]
    GtvArray<GtvBigInteger>, // B [x, y],
    GtvArray<GtvBigInteger>, // C [x, y],
    GtvArray<GtvBigInteger>, // Z [x, y],
    GtvArray<GtvBigInteger>, // T1 [x, y],
    GtvArray<GtvBigInteger>, // T2 [x, y],
    GtvArray<GtvBigInteger>, // T3 [x, y],
    GtvArray<GtvBigInteger>, // Wxi [x, y],
    GtvArray<GtvBigInteger>, // Wxiw [x, y],
    GtvBigInteger, // eval a
    GtvBigInteger, // eval b
    GtvBigInteger, // eval c
    GtvBigInteger, // eval s1
    GtvBigInteger, // eval s2
    GtvBigInteger, // eval zW
]
```

Below is an example, encoded in YAML:

```yaml
- - 17240610895659112949534001603701487710730940295991952755745011006139522106674L
  - 4772119288516329823087653880107681332372200263329323476733916710882831369530L
- - 1066870806508637499522533526183719541885655028320057420744469071125965790937L
  - 9050184939564542549799229060000742610832481810843967408857342596203730164973L
- - 15463964919858186598280841355736494185713791384787235458042266261387833572055L
  - 4033198885147731294912605335618993946535150894243278300895804351308778021148L
- - 13314232133429282957426394845542594689960599455696295326181087508261988135958L
  - 6726774866727232423891044501481882711606662875213919788592931207668199511134L
- - 11477851224523800471977052053956134542038620662885239253551843921183495762657L
  - 1590616074175293792422812111223960782086069811407604354065515355911492138601L
- - 19144946046404274069535748524984351516784756103697806670033204400950612647420L
  - 20955703061030466815229350413332387892120252887245295992391933558119204625452L
- - 18641249027415978142717610345213201006518729900812555724672666478768848741080L
  - 7709122934966784529434001299909412664983515103475964572392406464756602121736L
- - 5532009138210374634555033876791398012726251642501243460335115926986289440120L
  - 21280439037849521082055636841942522236565945387244007758539197915158660752272L
- - 16225487172195032997507999032918210083334407079784703530274485657664286163914L
  - 5244314046096985624987135440112545738503112293909515584344161968246502993983L
- 18594537782036243834188122006302786843242996305182829561580194742951568433110L
- 5646341342826424190570074044312889606205128365636955085996842172782326332353L
- 11079793317617318421423123171489222850306910488243954707001624446305707159712L
- 12299142744860077154516806188009478469163761012933091218794492959022547645689L
- 10683975730805954151221472170718530875595467202275033992663364209123903731746L
- 7165653556414579314670554955805384742835045579516197139471696967034528177565L
```
