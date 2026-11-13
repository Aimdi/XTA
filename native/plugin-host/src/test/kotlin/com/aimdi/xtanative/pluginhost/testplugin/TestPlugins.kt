package com.aimdi.xtanative.pluginhost.testplugin

import java.util.function.Supplier

// Plugin entry classes for the tests: their bytes go into test JARs, loaded
// with a parent that cannot see this package, as the app cannot see a plugin's.

/** An entry class made with its no-argument constructor, seen by the app through an interface the app knows. */
class HelloPlugin : Supplier<String> {
    override fun get(): String = "hello from a plugin"
}

/** An entry class the host cannot make: it has no no-argument constructor. */
class NeedsArgument(private val name: String) : Supplier<String> {
    override fun get(): String = name
}
