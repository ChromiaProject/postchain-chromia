package net.postchain.mc.test

import net.postchain.base.PeerInfo
import net.postchain.common.hexStringToByteArray

class TestPeerInfos {

    companion object {

        val peerInfo0 = PeerInfo(
                "127.0.0.1",
                9870,
                "0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57".hexStringToByteArray()
        )

        val peerInfo1 = PeerInfo(
                "127.0.0.1",
                9871,
                "035676109c54b9a16d271abeb4954316a40a32bcce023ac14c8e26e958aa68fba9".hexStringToByteArray()
        )

        val peerInfo2 = PeerInfo(
                "127.0.0.1",
                9872,
                "03f811d3e806e6d093a4bcce49c145ba78f9a4b2fbd167753ecab2a13530b081f8".hexStringToByteArray()
        )

        val peerInfo3 = PeerInfo(
                "127.0.0.1",
                9873,
                "03ef3f5be98d499b048ba28b247036b611a1ced7fcf87c17c8b5ca3b3ce1ee23a4".hexStringToByteArray()
        )
    }

}