<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <title>MCU</title>
</head>


<style>
    .column {
        float: left;
        width: 25%;
        /*font-size: 12px;*/
    }

    .row:after {
        content: "";
        display: table;
        clear: both;
    }

    .column-dapp-name {
        float: left;
        width: 60pt;
        height: 20pt;
        /*background-color: red;*/
        font-family: monospace;
        /*font-size: 12px;*/
    }

    .column-dapp-cont {
        float: left;
        width: 40pt;
        height: 20pt;
        /*background-color: red;*/
        font-family: monospace;
        /*font-size: 12px;*/
    }

    .column-dapp-actions {
        float: left;
        width: 150pt;
        height: 20pt;
        /*background-color: green;*/
        font-family: monospace;
        /*font-size: 12px;*/
    }

</style>


<script src="https://ajax.googleapis.com/ajax/libs/jquery/3.2.1/jquery.min.js"></script>

<script>

    let doClarify = true;
    // console.log( "JSON Data: " + JSON.stringify(json, null, '\t') );

    $.getJSON("${node0.apiUrl}/_debug", function (json) {
        var content = JSON.stringify(json, null, '  ');
        content = clarify(content)
        $('#div-0').text(content);
    });

    function clarify(content) {
        if (doClarify) {
            content = content
                .replaceAll("020CCD8A16F1CA7433D2EA5880D727AB8563B25F33CAA2201E7691AB373FBABFCF", "node0")
                .replaceAll("0295D20F3E4011AC18BEC9996AB1A5551FE4981C80B66665561AA0EB12D57DA934", "node1")
                .replaceAll("02ABFCBB7665C20BDBF9E9B58B0870514CF5ECD94EB2241DE28CC0E2AECD1BB7F7", "node2")
                .replaceAll("03EA865C827F4CD633BD36B0E4C1C83359F262C23F6ED49D4436D0086DC68BBB81", "node3")
                .replaceAll("03C7E6590FE1587AF45C474AF568B4122E64ABDFB093E922EB5C7CB75986F7F5C0", "node4")
                .replaceAll("035802732616AD88DC3B9EC770920FB0947C3346756F946E0131968C1A613DEC99", "node5")
                .replaceAll("020EE9063CED9B47CC60EB931443C01F3FD7A0DAB4B2D0505D3A2FAAD4FA9ABF13", "node6")

                .replaceAll("956AB6DB267A1DB47BDBBA4D423A162D8BA4596BA0F97152C4DD5D1E4B7C17DF", "chromia0")
                .replaceAll("F4BFBB70A9F0A91540AC57F4F1F9DF9E19AA46CB4DE3DA3F86F2E925DB883016", "cities")
                .replaceAll("1332F03A3E0AF426C810970C74FABDC5188D4C8D8FDC9417E47099D7399AC522", "books")
                .replaceAll("D0704BF76D892AA161F74AB9092A2131D7F3CA96A66FEAC6C0EB3C9207C029FD", "dapp0")
                .replaceAll("323ECC017DB717DBE1A6CF48095C5D795A5C05274B38DB81066DDF6DB2A98945", "dapp1")
                .replaceAll("A9599992088B7E32FB365FD484C87227A83DF387CC99A88AE47948A9EC4F8CD8", "dapp2")
                .replaceAll("86E2043D539F37F58292531DDD59FAC7C0F3DD3A87F4CDA074F0DA72AFF8F03F", "dapp3")
                .replaceAll("EB7387FDEF741ED8B672336BD5B1EDE7370191736ADBE87C032AC13407E97861", "dapp4")
                .replaceAll("94F295781F900902DF5EE3F8225D957FA46178F8C7CBC92818DA1BFA77576165", "dapp5")
                .replaceAll("0B942264C7E60D1921C31DCCCDD5A05BAAF4CB6487688C24220CD970A03768BE", "dapp6")

                .replaceAll("ValidatorWorker", "signer")
                .replaceAll("ReadOnlyWorker", "replica")

                .replaceAll("blockchain-rid", "brid")
                .replaceAll("blockchain-node-type", "node-type")

        }

        return content
    }

    function init() {
        $.post("/actions/init");
        $('#div-chain0').css('color', 'forestgreen');
    }

    function launch(dappName) {
        $.post("/actions/launch/" + dappName);
        $('#div-' + dappName).css('color', 'forestgreen');
    }

    function pause(dappName) {
        $.post("/actions/pause/" + dappName);
        $('#div-' + dappName).css('color', 'red');
    }

    function resume(dappName) {
        $.post("/actions/resume/" + dappName);
        $('#div-' + dappName).css('color', 'forestgreen');
    }

    function configure(dappName, configName) {
        $.post("/actions/configure/" + dappName + "/" + configName);
    }

    function postTx_1mb_blob(dappName) {
        $.post("/actions/tx/" + dappName + "/add_blob");
    }

</script>


<body>

<h3 style="font-family: monospace;">Dapps</h3>

<div class="row">
    <div class="column-dapp-name" id="div-chain0">${chain0Name}</div>
    <div class="column-dapp-cont"></div>
    <div class="column-dapp-actions">
        <a href="#" onclick="init()">init</a>
    </div>
    <div class="column-dapp-actions">
        <a href="#" onclick="configure('${chain0Name}', 'c1')">c1</a>
        <a href="#" onclick="configure('${chain0Name}', 'c2')">c2</a>
    </div>
    <div class="column-dapp-actions"></div>
</div>

<div class="row">
    <div class="column-dapp-name" id="div-${dapp0.name}">${dapp0.name}</div>
    <div class="column-dapp-cont">${dapp0.cont}</div>
    <div class="column-dapp-actions">
        <a href="#" onclick="launch('${dapp0.name}')">start</a>
        <a href="#" onclick="pause('${dapp0.name}')">pause</a>
        <a href="#" onclick="resume('${dapp0.name}')">resume</a>
    </div>
    <div class="column-dapp-actions"></div>
    <div class="column-dapp-actions"></div>
</div>

<div class="row">
    <div class="column-dapp-name" id="div-${dapp1.name}">${dapp1.name}</div>
    <div class="column-dapp-cont">${dapp1.cont}</div>
    <div class="column-dapp-actions">
        <a href="#" onclick="launch('${dapp1.name}')">start</a>
        <a href="#" onclick="pause('${dapp1.name}')">pause</a>
        <a href="#" onclick="resume('${dapp1.name}')">resume</a>
    </div>
    <div class="column-dapp-actions"></div>
    <div class="column-dapp-actions"></div>
</div>

<div class="row">
    <div class="column-dapp-name" id="div-${dapp2.name}">${dapp2.name}</div>
    <div class="column-dapp-cont">${dapp2.cont}</div>
    <div class="column-dapp-actions">
        <a href="#" onclick="launch('${dapp2.name}')">start</a>
        <a href="#" onclick="pause('${dapp2.name}')">pause</a>
        <a href="#" onclick="resume('${dapp2.name}')">resume</a>
    </div>
    <div class="column-dapp-actions"></div>
    <div class="column-dapp-actions">
        <a href="#" onclick="postTx_1mb_blob('${dapp2.name}')">post_1mb_blob</a>
    </div>
</div>

<div class="row">
    <div class="column-dapp-name" id="div-${dapp3.name}">${dapp3.name}</div>
    <div class="column-dapp-cont">${dapp3.cont}</div>
    <div class="column-dapp-actions">
        <a href="#" onclick="launch('${dapp3.name}')">start</a>
        <a href="#" onclick="pause('${dapp3.name}')">pause</a>
        <a href="#" onclick="resume('${dapp3.name}')">resume</a>
    </div>
    <div class="column-dapp-actions"></div>
    <div class="column-dapp-actions"></div>
</div>

<div class="row">
    <div class="column-dapp-name" id="div-${dapp4.name}">${dapp4.name}</div>
    <div class="column-dapp-cont">${dapp4.cont}</div>
    <div class="column-dapp-actions">
        <a href="#" onclick="launch('${dapp4.name}')">start</a>
        <a href="#" onclick="pause('${dapp4.name}')">pause</a>
        <a href="#" onclick="resume('${dapp4.name}')">resume</a>
    </div>
    <div class="column-dapp-actions"></div>
    <div class="column-dapp-actions"></div>
</div>

<div class="row">
    <div class="column-dapp-name" id="div-${dapp5.name}">${dapp5.name}</div>
    <div class="column-dapp-cont">${dapp5.cont}</div>
    <div class="column-dapp-actions">
        <a href="#" onclick="launch('${dapp5.name}')">start</a>
        <a href="#" onclick="pause('${dapp5.name}')">pause</a>
        <a href="#" onclick="resume('${dapp5.name}')">resume</a>
    </div>
    <div class="column-dapp-actions"></div>
    <div class="column-dapp-actions"></div>
</div>

<div class="row">
    <div class="column-dapp-name" id="div-${dapp6.name}">${dapp6.name}</div>
    <div class="column-dapp-cont">${dapp6.cont}</div>
    <div class="column-dapp-actions">
        <a href="#" onclick="launch('${dapp6.name}')">start</a>
        <a href="#" onclick="pause('${dapp6.name}')">pause</a>
        <a href="#" onclick="resume('${dapp6.name}')">resume</a>
    </div>
    <div class="column-dapp-actions"></div>
    <div class="column-dapp-actions"></div>
</div>

<div class="row">
    <div class="column-dapp-name" id="div-${dapp7.name}">${dapp7.name}</div>
    <div class="column-dapp-cont">${dapp7.cont}</div>
    <div class="column-dapp-actions">
        <a href="#" onclick="launch('${dapp7.name}')">start</a>
        <a href="#" onclick="pause('${dapp7.name}')">pause</a>
        <a href="#" onclick="resume('${dapp7.name}')">resume</a>
    </div>
    <div class="column-dapp-actions"></div>
    <div class="column-dapp-actions"></div>
</div>

<div class="row">
    <div class="column-dapp-name" id="div-${dapp8.name}">${dapp8.name}</div>
    <div class="column-dapp-cont">${dapp8.cont}</div>
    <div class="column-dapp-actions">
        <a href="#" onclick="launch('${dapp8.name}')">start</a>
        <a href="#" onclick="pause('${dapp8.name}')">pause</a>
        <a href="#" onclick="resume('${dapp8.name}')">resume</a>
    </div>
    <div class="column-dapp-actions"></div>
    <div class="column-dapp-actions"></div>
</div>



<div style="height: 20pt"></div>


<h3 style="font-family: monospace;">Nodes</h3>
<!-- <div id="div-1"/> -->
<div class="row">
    <div class="column">
        <pre><b>${node0.name}</b></pre>
    </div>
    <div class="column">
        <pre><b>node1</b></pre>
    </div>
    <div class="column">
        <pre><b>node2</b></pre>
    </div>
    <div class="column">
        <pre><b>node3</b></pre>
    </div>
</div>
<div class="row">
    <div class="column">
        <pre id="div-0"></pre>
    </div>
    <div class="column">
        <pre id="div-1"></pre>
    </div>
    <div class="column">
        <pre id="div-2"></pre>
    </div>
    <div class="column">
        <pre id="div-3"></pre>
    </div>
</div>

</body>

</html>