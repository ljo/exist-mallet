exist-mallet-topic-modeling
===========================

Integrates the Mallet Machine Learning and Topic Modeling library into eXist-db.

## Compile and install

1. clone the github repository: https://github.com/ljo/exist-mallet
2. edit local.build.properties and set exist.dir to point to your eXist-db install directory
3. call "ant" in the directory to create a .xar
4. upload the xar into eXist-db using the dashboard

## Functions

There are currently three main function groups:

### topics:create-instances-*

Processes resources in the provided collection hierachy and creates serialized instances which can be used by nearly all Mallet sub-packages. Returns the path to the stored instances document.

topics:create-instances-collection($instances-doc as xs:anyURI, $collection-uri as xs
:anyURI, $qname as xs:QName?) as xs:string?

topics:create-instances-node($instances-doc as xs:anyURI, $node as node()+) as 
xs:string?

topics:create-instances-string($instances-doc as xs:anyURI, $text as xs:string+) 
as xs:string?

###   topics:topic-model-sample and topics:topic-model
Processes instances and creates a topic model which can be used for inference. Returns the specified number of top topics. All other parameters use default values. Runs the model for 50 iterations and stops (this is for testing only, for real applications, use 1000 to 2000 iterations).

topics:topic-model-sample($instances-doc as xs:anyURI) as node()+

topics:topic-model-sample($instances-doc as xs:anyURI, $number-of-topics-to-show 
as xs:integer, $language as xs:string?) as node()+

Processes instances and creates a topic model which can be used for inference. Returns the specified number of top ranked words per topic.

topics:topic-model($instances-doc as xs:anyURI, $number-of-words-per-topic as xs
:integer, $number-of-topics as xs:integer, $number-of-iterations as xs:integer?, 
$number-of-threads as xs:integer?, $alpha_t as xs:double?, $beta_w as xs:double
?, $language as xs:string?) as node()+


### and topics:topic-model-inference
Processes instances and creates a topic model which can be used for inference. Returns the topic probabilities for the inferenced instances.

topics:topic-model-inference($instances-doc as xs:anyURI, $number-of-words-per
-topic as xs:integer, $number-of-topics as xs:integer, $number-of-iterations as 
xs:integer?, $number-of-threads as xs:integer?, $alpha_t as xs:double?, $beta_w 
as xs:double?, $language as xs:string?, $instances-inference-doc as xs:anyURI) 
as node()+

Processes new instances and applies the stored topic model's inferencer. Returns the topic probabilities for the inferenced instances.

topics:topic-model-inference($topic-model-doc as xs:anyURI, $instances-inference
-doc as xs:anyURI, $number-of-iterations as xs:integer, $thinning as xs:integer
?, $burn-in as xs:integer?) as node()+

## Usage example

```xquery
xquery version "3.0";
import module namespace tm="http://exist-db.org/xquery/mallet-topic-modeling";
declare namespace tei="http://www.tei-c.org/ns/1.0";

let $text := 
("Ett lite större test än det borde gå an om några dagar. 
Vad är ämnet om inte några ämnesord kommer med? 
eXist-db applikationen får berätta när den funkar. ", "Dessutom nu med två texter som strängargument. ")
let $text2 := (<text>{$text[1]}</text>, <text>{$text[2]}</text>)
let $text3 := xs:anyURI("/db/dramawebben/data/works/AgrellA_Domd")
let $instances-doc-suffix := ".mallet"
let $instances-doc-prefix := "/db/apps/mallet-topic-modeling/resources/instances/topic-example"
let $instances-path := $instances-doc-prefix || $instances-doc-suffix
let $instances-path2 := $instances-doc-prefix || "2" || $instances-doc-suffix
let $instances-path3 := $instances-doc-prefix || "3" || $instances-doc-suffix
let $instances-path4 := $instances-doc-prefix || "4" || $instances-doc-suffix

let $mode := 3
let $call-type := ("string", "node", "collection")[$mode]
let $instances-uri := xs:anyURI(($instances-path, $instances-path2, $instances-path3)[$mode])
let $new-instances-uri := xs:anyURI($instances-path4)
let $create-instances-p := true()

let $created := if ($create-instances-p) then 
    switch ($call-type)
        case "string" return tm:create-instances-string($instances-uri, $text)
        case "node" return tm:create-instances-node($instances-uri, $text2)
        case "collection" return tm:create-instances-collection($instances-uri, $text3, xs:QName("tei:body"))
        default return tm:create-instances-string($instances-uri, $text)
    else ()
    
return
    tm:topic-model-sample($instances-uri, 5, "sv")
(: tm:topic-model-inference($instances-uri, 5, 25, 50, (), (), (), "sv", $new-instances-uri) :)
```
