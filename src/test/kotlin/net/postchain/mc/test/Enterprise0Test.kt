package net.postchain.mc.test

import net.postchain.mc.config.app.AppConfig
import org.apache.commons.configuration2.MapConfiguration
import org.junit.Test

class Enterprise0Test : ManagedModeTest() {
    /*
    api.url=http://127.0.0.1:7740
brid=01FB20732737BC0E7350459BE304769902CAEFEF501571FD0191D6F5F24675B9
pubkey=0373599a61cc6b3bc02a78c34313e1737ae9cfd56b9bb24360b437d469efdf3b15
privkey=a68957ba735f98f8d8169ee54fccf2ccf193d97d95e46342bb5e05007c51f324
     */
    val clientConfigMap = mapOf(
            Pair("api.url", "http://127.0.0.1:7740"),
            Pair("brid", "74D3973F582A7C65768F55570C3361D8CE210B80937C14CA9E5DB74845DE6716"),
            Pair("pubkey", "03A692CDB2FD63037D883804A2028C5FBFD5E8A7E20E08BC387071220F8AD06710"),
            Pair("privkey", "FBCC46C6BABF35F7C7ABB599CD696F74375E4704D322EBD220FA4472C5C0AF68")
    )
    /*
    api.url=http://127.0.0.1:7740
    brid=01FB20732737BC0E7350459BE304769902CAEFEF501571FD0191D6F5F24675B9
    pubkey=03962AB49BC8D056C56A405DEFDA2448DE3A6AF65E6EA84019EE551A3526D0ADB0
    privkey=9EC6477E36921F519BC2F805BFA01E9D2AE9DFF2D761A1140C76EEEAFEC78453
    */

    val provConfigMap = mapOf(
            Pair("api.url", "http://127.0.0.1:7740"),
            Pair("brid", "74D3973F582A7C65768F55570C3361D8CE210B80937C14CA9E5DB74845DE6716"),
            Pair("pubkey", "036C9145D9F535ED54AE942DD581E19DFFF6FDDAA98568BB936E41A23C356AF413"),
            Pair("privkey", "4707C67377D5629AB3560ACD24F89C7273FE64C20BF1D6643A68D8E62051EC5E")
    )

    @Test
    fun testRegisterProvider() {
        val configFileName = "/net/postchain/mc/test/config/ai_blockchain_config.xml"
        val clientConfig = MapConfiguration(clientConfigMap)
        testRegisterProviderInternal(configFileName, AppConfig(clientConfig))
     }

    @Test
    fun testAddConfiguration() {
        val configFileName = "/net/postchain/mc/test/config/ai_blockchain_config.xml"
        val clientConfig = MapConfiguration(clientConfigMap)
        val provConfig = MapConfiguration(provConfigMap)
        testAddConfigurationInternal(configFileName, AppConfig(clientConfig), AppConfig(provConfig))
    }
}