package net.postchain.deployment

import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import org.w3c.dom.Element
import java.io.StringWriter
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

object DeployXmlParser {
    fun parse(path: Path): ParsedConfiguration {
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val deployXml = builder.parse(path.toFile())
        val deployElement = deployXml.documentElement
        val chainElement = deployElement.getElementsByTagName("chain").item(0) as? Element
            ?: throw UserMistake("deploy.xml missing chain element")
        val chainName = chainElement.getAttribute("name")
        if (chainName.isBlank()) throw UserMistake("Chain must have a name")

        val container = chainElement.getAttribute("container").ifBlank { null }
        val blockchainRid = chainElement.getAttribute("blockchain-rid").ifBlank { null }

        val runXml = builder.newDocument()
        val runElement = runXml.createElement("run")
        runXml.appendChild(runElement)

        val nodesElement = runXml.createElement("nodes")
        runElement.appendChild(nodesElement)
        val nodeConfigElement = runXml.createElement("config")
        nodeConfigElement.setAttribute("add-signers", "false")
        nodeConfigElement.textContent = "#"
        nodesElement.appendChild(nodeConfigElement)

        val chainsElement = runXml.createElement("chains")
        runElement.appendChild(chainsElement)

        val runChainElement = runXml.createElement("chain")
        runChainElement.setAttribute("iid", "100")
        runChainElement.setAttribute("name", chainName)
        chainsElement.appendChild(runChainElement)

        val configElement = runXml.createElement("config")
        configElement.setAttribute("height", "0")

        for (index in 0 until chainElement.childNodes.length) {
            val child = chainElement.childNodes.item(index)
            configElement.appendChild(runXml.importNode(child, true))
        }

        runChainElement.appendChild(configElement)

        val domSource = DOMSource(runXml)
        val transformer: Transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        transformer.setOutputProperty(OutputKeys.METHOD, "xml")
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        val sw = StringWriter()
        transformer.transform(domSource, StreamResult(sw))

        return ParsedConfiguration(
                sw.buffer.toString(),
                blockchainRid?.let { BlockchainRid(it.hexStringToByteArray()) },
                container
        )
    }
}
