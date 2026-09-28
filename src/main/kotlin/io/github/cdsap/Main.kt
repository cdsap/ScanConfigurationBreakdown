package io.github.cdsap

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import io.github.cdsap.cli.CompareCommand
import io.github.cdsap.cli.ListCommand

/**
 * Entry point. The commands live in [io.github.cdsap.cli]; the two-stage read they drive lives
 * in [Pipeline].
 */
class ScanConfigurationBreakdown : NoOpCliktCommand(name = "scb") {
    override fun help(context: Context) =
        "Read the Build Scan configuration-phase data of the Develocity builds matching a filter."

    override fun helpEpilog(context: Context) =
        "Listing builds uses the access key; reading their scan data uses whatever the instance " +
            "requires there — nothing on an instance with anonymous access, a browser session " +
            "cookie (--cookie) on one behind SSO."
}

fun main(args: Array<String>) =
    ScanConfigurationBreakdown()
        .subcommands(ListCommand(), CompareCommand())
        .main(args)
