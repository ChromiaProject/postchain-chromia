package net.postchain.deployment

import net.postchain.common.exception.UserMistake
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
    fun parse(path: Path): String {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val deployXml = builder.parse(path.toFile())
        val deployElement = deployXml.documentElement
        val chainElement = deployElement.getElementsByTagName("chain").item(0) as? Element
            ?: throw UserMistake("deploy.xml missing chain element")
        val chainName = chainElement.getAttribute("name")

        val runXml = builder.newDocument()
        val runElement = runXml.createElement("run")
        runXml.appendChild(runElement)

        val nodesElement = runXml.createElement("nodes")
        runElement.appendChild(nodesElement)
        val nodeConfigElement = runXml.createElement("config")
        nodeConfigElement.textContent = "#"
        nodesElement.appendChild(nodeConfigElement)

        val chainsElement = runXml.createElement("chains")
        runElement.appendChild(chainsElement)

        val runChainElement = runXml.createElement("chain")
        runChainElement.setAttribute("iid", "100")
        runChainElement.setAttribute("name", chainName)
        chainsElement.appendChild(runChainElement)

        val configNodes = chainElement.getElementsByTagName("config")
        var lastVersion = -1L
        for (i in 0 until configNodes.length) {
            val configElement = configNodes.item(i) as Element
            val version = configElement.getAttribute("version").toLong()
            if (i == 0 && version != 0L) throw UserMistake("First version must be 0")
            if (version <= lastVersion) throw UserMistake("Versions must be in order")

            configElement.setAttribute("height", version.toString())
            configElement.removeAttribute("version")

            runChainElement.appendChild(runXml.importNode(configElement, true))

            lastVersion = version
        }

        val domSource = DOMSource(runXml)
        val transformer: Transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        transformer.setOutputProperty(OutputKeys.METHOD, "xml")
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        val sw = StringWriter()
        transformer.transform(domSource, StreamResult(sw))
        return sw.buffer.toString()
    }
}
