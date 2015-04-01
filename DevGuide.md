cdmi-spec Developer's Guide
=========

`cdmi-spec` is coded in [`Scala`](http://www.scala-lang.org) and uses Twitter's [`finagle`](http://twitter.github.io/finagle/) as the underlying http library. The build and packaging process is orchestrated using [`maven`](http://www.maven.org). The code is hosted on [GitHub](https://github.com), so we use [`git`](http://git-scm.com).

What `cdmi-spec` provides is a set of web hooks and reference implementations for the `REST`-like API of `CDMI`. In effect, it interpets the specification and, given an incoming HTTP request, it understands what CDMI-specific action this is about and delegates to respective specification-handling code. For example, `cdmi-spec` can understand that a given request is a *PUT CDMI container* request and delegates to an appropriate `put_cdmi_container` method (the exact name may defer).

So, let's say you want to develop CDMI connector for your storage service. These are the necessary steps to follow.

**Note** *If you follow the installation instructions below and you get an error, please open a [ticket](https://github.com/grnet/cdmi-spec/issues) on this project*.

`cdmi-spec` is being developed with Java 7, maven 3.2+ (tested with 3.2.5) and Scala 2.11. Official versions are also deployed to [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22gr.grnet%22%20cdmi-spec)


If you need to setup a maven-based build, you can have a look at project [`snf-cdmi`](https://github.com/grnet/snf-cdmi), which uses `cdmi-spec` to implement a [Pithos](https://www.synnefo.org/docs/synnefo/latest/pithos.html)-based connector and respective server. In particular, the declaration of the `cdmi-spec` [dependency](https://github.com/grnet/snf-cdmi/blob/wip_v0.4/pom.xml#L74) is highly relevant.

## Implementation walkthrough

The *first layer* of CDMI implementation is provided by trait [CdmiRestService](https://github.com/grnet/cdmi-spec/blob/wip_v0.4/src/main/scala/gr/grnet/cdmi/service/CdmiRestService.scala). It serves two purposes:

1. It defines the [routing table](https://github.com/grnet/cdmi-spec/blob/wip_v0.4/src/main/scala/gr/grnet/cdmi/service/CdmiRestService.scala#L292) for the CDMI REST endpoints.
2. It provides a means to setup and run a [standalone CDMI server](https://github.com/grnet/cdmi-spec/blob/wip_v0.4/src/main/scala/gr/grnet/cdmi/service/CdmiRestService.scala#L400) with complete support for SSL etc.

For example, the relevant implementation for the [Pithos](https://www.synnefo.org/docs/synnefo/latest/pithos.html) connector and server, in project [`snf-cdmi`](https://github.com/grnet/snf-cdmi), is the singleton object [StdCdmiPithosServer](https://github.com/grnet/snf-cdmi/blob/wip_v0.4/src/main/scala/gr/grnet/cdmi/service/StdCdmiPithosServer.scala#L81).

*As a new connector implementor, you do not need to do anything at the first layer*.

The *second layer* of CDMI implementation is provided by trait [CdmiRestServiceHandlers](https://github.com/grnet/cdmi-spec/blob/wip_v0.4/src/main/scala/gr/grnet/cdmi/service/CdmiRestServiceHandlers.scala). When [CdmiRestService](https://github.com/grnet/cdmi-spec/blob/wip_v0.4/src/main/scala/gr/grnet/cdmi/service/CdmiRestService.scala) intercepts a CDMI-specific call, it interprets and delegates it to an appropriate handler in [CdmiRestServiceHandlers](https://github.com/grnet/cdmi-spec/blob/wip_v0.4/src/main/scala/gr/grnet/cdmi/service/CdmiRestServiceHandlers.scala). For example, the endpoint that [requests](https://github.com/grnet/cdmi-spec/blob/wip_v0.4/src/main/scala/gr/grnet/cdmi/service/CdmiRestService.scala#L326) the root `cdmi_capabilities/` (note the trailing slash) is delegated to method [`handleCapabilitiesCall`](https://github.com/grnet/cdmi-spec/blob/wip_v0.4/src/main/scala/gr/grnet/cdmi/service/CdmiRestServiceHandlers.scala#L397).

*As a new connector implementor, you do not need to do anything at the second layer*.

The *third layer* of CDMI implementation is provided by trait [CdmiRestServiceMethods](https://github.com/grnet/cdmi-spec/blob/wip_v0.4/src/main/scala/gr/grnet/cdmi/service/CdmiRestServiceMethods.scala). So, for example, if method [handleCapabilitiesCall](https://github.com/grnet/cdmi-spec/blob/wip_v0.4/src/main/scala/gr/grnet/cdmi/service/CdmiRestServiceHandlers.scala#L397) of [CdmiRestServiceHandlers](https://github.com/grnet/cdmi-spec/blob/wip_v0.4/src/main/scala/gr/grnet/cdmi/service/CdmiRestServiceHandlers.scala) decides that the call is valid, it delegates to the *actual* implementation [`GET_capabilities`](https://github.com/grnet/cdmi-spec/blob/wip_v0.4/src/main/scala/gr/grnet/cdmi/service/CdmiRestServiceMethods.scala#L29). Note the `GET` prefix. All such methods in [CdmiRestServiceMethods](https://github.com/grnet/cdmi-spec/blob/wip_v0.4/src/main/scala/gr/grnet/cdmi/service/CdmiRestServiceMethods.scala) have a prefix that denotes the method of the repsective HTTP request, then an underscore `_` and then a suffix that denotes the relevant CDMI operation that needs to be carried out. For example, a few of these methods are:

* [`PUT_object_noncdmi`](https://github.com/grnet/cdmi-spec/blob/wip_v0.4/src/main/scala/gr/grnet/cdmi/service/CdmiRestServiceMethods.scala#L71)
* [`GET_object_noncdmi`](https://github.com/grnet/cdmi-spec/blob/wip_v0.4/src/main/scala/gr/grnet/cdmi/service/CdmiRestServiceMethods.scala#L86)
* [`DELETE_object_cdmi`](https://github.com/grnet/cdmi-spec/blob/wip_v0.4/src/main/scala/gr/grnet/cdmi/service/CdmiRestServiceMethods.scala#L108)

**The essense:** *What you need to implement is the third layer, meaning the methods in [CdmiRestServiceMethods](https://github.com/grnet/cdmi-spec/blob/wip_v0.4/src/main/scala/gr/grnet/cdmi/service/CdmiRestServiceMethods.scala)*.

You may wish to have a look at the [Pithos connector](https://github.com/grnet/snf-cdmi/blob/wip_v0.4/src/main/scala/gr/grnet/cdmi/service/StdCdmiPithosServer.scala#L81), for example:

* [`GET_container_cdmi`](https://github.com/grnet/snf-cdmi/blob/wip_v0.4/src/main/scala/gr/grnet/cdmi/service/StdCdmiPithosServer.scala#L391)
* [`PUT_container_cdmi_create_or_update`](https://github.com/grnet/snf-cdmi/blob/wip_v0.4/src/main/scala/gr/grnet/cdmi/service/StdCdmiPithosServer.scala#L513)
* [`DELETE_container_cdmi`](https://github.com/grnet/snf-cdmi/blob/wip_v0.4/src/main/scala/gr/grnet/cdmi/service/StdCdmiPithosServer.scala#L540) and the helper method [`DELETE_container_`](https://github.com/grnet/snf-cdmi/blob/wip_v0.4/src/main/scala/gr/grnet/cdmi/service/StdCdmiPithosServer.scala#L518).

