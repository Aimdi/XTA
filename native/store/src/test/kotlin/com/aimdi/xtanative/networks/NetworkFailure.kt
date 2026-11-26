package com.aimdi.xtanative.networks

/** The `:networks` interface by its name, which `:store` and its tests do not depend on: what CrashIssue.shouldReport looks for. */
interface NetworkFailure

/** A failed read of another network, as `:networks`' NetworkRequestException is one. */
class FakeNetworkRequestException(message: String) : Exception(message), NetworkFailure
