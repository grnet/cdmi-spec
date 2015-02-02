cdmi-spec Developer's Guide
=========

`cdmi-spec` is coded in [`Scala`](http://www.scala-lang.org) and uses Twitter's [`finagle`](http://twitter.github.io/finagle/) as the underlying http library. The build and packaging process is orchestrated using [`maven`](http://www.maven.org). The code is hosted on [GitHub](https://github.com), so we use [`git`](http://git-scm.com).

What `cdmi-spec` provides is a set of web hooks and reference implementations for the `REST`-like API of `CDMI`. In effect, it interpets the specification and, given an incoming HTTP request, it understands what CDMI-specific action this is about and delegates to respective specification-handling code. For example, `cdmi-spec` can understand that a given request is a *PUT CDMI container* request and delegates to an appropriate `put_cdmi_container` method (the exact name may defer).

So, let's say you want to develop CDMI connector for your storage service. These are the necessary steps to follow.

**Note** *If you follow the installation instructions below and you get an error, please open a [ticket](https://github.com/grnet/cdmi-spec/issues) on this project*.

## Install tools
Please install Java 7, maven 3.2+, scala 2.10.4.

## Install dependencies

### typedkey
This is a manual step to install a dependency that has not been published to Maven Central:

```
$ git clone https://github.com/loverdos/typedkey
$ cd typedkey
$ mvn install
```

`cdmi-spec` currently relies on version `0.8.0-SNAPSHOT` of the
`typedkey` library. The last commit we have verified everything is OK is [20d26d15f1ffb74409f80e43fd1dd739fc30a1eb](https://github.com/loverdos/typedkey/commit/20d26d15f1ffb74409f80e43fd1dd739fc30a1eb)

The above step will install the library in your local maven cache. This is usually under the `.m2` folder in your home folder.

### snf-common-j
Another library that needs to be pulled separately.

```
$ git clone https://github.com/grnet/snf-common-j
$ cd snf-common-j
$ mvn install
```


## Clone the repo

```
$ git clone https://github.com/grnet/cdmi-spec
```

The current version of `cdmi-spec` is `0.3.0-SNAPSHOT`.

## Test everything is OK

Run maven to install the library in your local maven cache. Note that this may take a while, especially if your local maven cache does not have the needed dependencies, which will be downloaded on demand.


```
$ cd cdmi-spec
$ mvn install

...
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 5.122 s
[INFO] Finished at: 2015-02-02T13:29:39+02:00
[INFO] Final Memory: 21M/227M
[INFO] ------------------------------------------------------------------------

```

We have tested the procedure against commit [39545c0a80c2ba37bde5d0e38517a80dd57dfbb9](https://github.com/grnet/cdmi-spec/commit/39545c0a80c2ba37bde5d0e38517a80dd57dfbb9)

Now you have `cdmi-spec` installed locally and you can start developing your own connector. You can use whichever build system you prefer. The maven coordinates for `cdmi-spec` are:

```
<dependency>
    <groupId>gr.grnet</groupId>
    <artifactId>cdmi-spec</artifactId>
    <version>0.3.0-SNAPSHOT</version>
</dependency>
```

If you need to setup a maven-based build, you can have a look at project [`snf-cdmi`](https://github.com/grnet/snf-cdmi), which uses `cdmi-spec` to implement a [Pithos](https://www.synnefo.org/docs/synnefo/latest/pithos.html)-based connector and respective server. In particular, the declaration of the `cdmi-spec` [dependency](https://github.com/grnet/snf-cdmi/blob/db17f3d0794e9b12fadd49172a7c2d8074c9513c/pom.xml#L74) is highly relevant.

## Implementation walkthrough

The *first layer* of CDMI implementation is provided by trait [CdmiRestService](https://github.com/grnet/cdmi-spec/blob/39545c0a80c2ba37bde5d0e38517a80dd57dfbb9/src/main/scala/gr/grnet/cdmi/service/CdmiRestService.scala). It serves two purposes:

1. It defines the [routing table](https://github.com/grnet/cdmi-spec/blob/39545c0a80c2ba37bde5d0e38517a80dd57dfbb9/src/main/scala/gr/grnet/cdmi/service/CdmiRestService.scala#L265) for the CDMI REST endpoints.
2. It provides a means to setup and run a [standalone CDMI server](https://github.com/grnet/cdmi-spec/blob/39545c0a80c2ba37bde5d0e38517a80dd57dfbb9/src/main/scala/gr/grnet/cdmi/service/CdmiRestService.scala#L390) with complete support for SSL etc.

For example, the relevant implementation for the [Pithos](https://www.synnefo.org/docs/synnefo/latest/pithos.html) connector and server, in project [`snf-cdmi`](https://github.com/grnet/snf-cdmi), is the singleton object [StdCdmiPithosServer](https://github.com/grnet/snf-cdmi/blob/db17f3d0794e9b12fadd49172a7c2d8074c9513c/src/main/scala/gr/grnet/cdmi/service/StdCdmiPithosServer.scala#L82). *As a new connector implementor, you do not need to do anything at this first layer*.

The *second layer* of CDMI implementation is provided by trait [CdmiRestServiceHandlers](https://github.com/grnet/cdmi-spec/blob/39545c0a80c2ba37bde5d0e38517a80dd57dfbb9/src/main/scala/gr/grnet/cdmi/service/CdmiRestServiceHandlers.scala). When [CdmiRestService](https://github.com/grnet/cdmi-spec/blob/39545c0a80c2ba37bde5d0e38517a80dd57dfbb9/src/main/scala/gr/grnet/cdmi/service/CdmiRestService.scala) intercepts a CDMI-specific call, it delegates it to an appropriate handler in [CdmiRestServiceHandlers](https://github.com/grnet/cdmi-spec/blob/39545c0a80c2ba37bde5d0e38517a80dd57dfbb9/src/main/scala/gr/grnet/cdmi/service/CdmiRestServiceHandlers.scala). For example, the endpoint that [requests](https://github.com/grnet/cdmi-spec/blob/39545c0a80c2ba37bde5d0e38517a80dd57dfbb9/src/main/scala/gr/grnet/cdmi/service/CdmiRestService.scala#L311) the root `cdmi_capabilities/` (note the trailing slash) is delegated to method [`handleCapabilitiesCall`](https://github.com/grnet/cdmi-spec/blob/39545c0a80c2ba37bde5d0e38517a80dd57dfbb9/src/main/scala/gr/grnet/cdmi/service/CdmiRestServiceHandlers.scala#L392). *As a new connector implementor, you do not need to do anything at this second layer*.

The *third layer* of CDMI implementation is provided by trait [CdmiRestServiceMethods](https://github.com/grnet/cdmi-spec/blob/39545c0a80c2ba37bde5d0e38517a80dd57dfbb9/src/main/scala/gr/grnet/cdmi/service/CdmiRestServiceMethods.scala). So, for example, if method [handleCapabilitiesCall](https://github.com/grnet/cdmi-spec/blob/39545c0a80c2ba37bde5d0e38517a80dd57dfbb9/src/main/scala/gr/grnet/cdmi/service/CdmiRestServiceHandlers.scala#L392) of [CdmiRestServiceHandlers](https://github.com/grnet/cdmi-spec/blob/39545c0a80c2ba37bde5d0e38517a80dd57dfbb9/src/main/scala/gr/grnet/cdmi/service/CdmiRestServiceHandlers.scala) decides that the call is valid, it delegates to the *actual* implementation [`GET_capabilities`](https://github.com/grnet/cdmi-spec/blob/39545c0a80c2ba37bde5d0e38517a80dd57dfbb9/src/main/scala/gr/grnet/cdmi/service/CdmiRestServiceMethods.scala#L29). Note the `GET` prefix. All such methods in [CdmiRestServiceMethods](https://github.com/grnet/cdmi-spec/blob/39545c0a80c2ba37bde5d0e38517a80dd57dfbb9/src/main/scala/gr/grnet/cdmi/service/CdmiRestServiceMethods.scala) have a prefix that denotes the HTTP method used in the call, then an underscore `_` and then a suffix that denotes the relevant CDMI operation that needs to be carried out. For example, a few of these methods are:

* [`PUT_object_noncdmi`](https://github.com/grnet/cdmi-spec/blob/39545c0a80c2ba37bde5d0e38517a80dd57dfbb9/src/main/scala/gr/grnet/cdmi/service/CdmiRestServiceMethods.scala#L71)
* [`GET_object_noncdmi`](https://github.com/grnet/cdmi-spec/blob/39545c0a80c2ba37bde5d0e38517a80dd57dfbb9/src/main/scala/gr/grnet/cdmi/service/CdmiRestServiceMethods.scala#L86)
* [`DELETE_object_cdmi`](https://github.com/grnet/cdmi-spec/blob/39545c0a80c2ba37bde5d0e38517a80dd57dfbb9/src/main/scala/gr/grnet/cdmi/service/CdmiRestServiceMethods.scala#L108)

**The essense:** *What you need to implement is the third layer, meaning the methods in [CdmiRestServiceMethods](https://github.com/grnet/cdmi-spec/blob/39545c0a80c2ba37bde5d0e38517a80dd57dfbb9/src/main/scala/gr/grnet/cdmi/service/CdmiRestServiceMethods.scala)*.

Again, please have a look at the [Pithos connector](https://github.com/grnet/snf-cdmi/blob/db17f3d0794e9b12fadd49172a7c2d8074c9513c/src/main/scala/gr/grnet/cdmi/service/StdCdmiPithosServer.scala#L82), for example:

* [`GET_container_cdmi`](https://github.com/grnet/snf-cdmi/blob/db17f3d0794e9b12fadd49172a7c2d8074c9513c/src/main/scala/gr/grnet/cdmi/service/StdCdmiPithosServer.scala#L407)
* [`PUT_container_cdmi_create_or_update`](https://github.com/grnet/snf-cdmi/blob/db17f3d0794e9b12fadd49172a7c2d8074c9513c/src/main/scala/gr/grnet/cdmi/service/StdCdmiPithosServer.scala#L541)
* [`DELETE_container_cdmi`](https://github.com/grnet/snf-cdmi/blob/db17f3d0794e9b12fadd49172a7c2d8074c9513c/src/main/scala/gr/grnet/cdmi/service/StdCdmiPithosServer.scala#L572) and the helper method [`DELETE_container_`](https://github.com/grnet/snf-cdmi/blob/db17f3d0794e9b12fadd49172a7c2d8074c9513c/src/main/scala/gr/grnet/cdmi/service/StdCdmiPithosServer.scala#L546)

