/*
 * Copyright 2020-2022, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package nextflow.cli

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.exception.AbortOperationException
import nextflow.plugin.Plugins
import nextflow.secret.SecretsLoader
import nextflow.secret.SecretsProvider
/**
 * Implements the {@code secret} command
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Parameters(commandDescription = "Manage pipeline secrets (preview)")
class CmdSecret extends CmdBase implements UsageAware {

    interface SubCmd {
        String getName()
        void apply()
        void usage(List<String> result)
    }

    static public final String NAME = 'secrets'

    private List<SubCmd> commands = []

    String getName() {
        return NAME
    }

    @Parameter(names=['-n','-name'], description = 'Secret name')
    String secretName

    @Parameter(names=['-v','-value'], description = 'Secret value')
    String secretValue

    @Parameter(hidden = true)
    List<String> args

    private SecretsProvider provider

    CmdSecret() {
        commands.add( new GetCmd() )
        commands.add( new PutCmd() )
        commands.add( new ListCmd() )
        commands.add( new DeleteCmd() )
    }

    /**
     * Print the command usage help
     */
    void usage() {
        usage(args)
    }

    /**
     * Print the command usage help
     *
     * @param args The arguments as entered by the user
     */
    void usage(List<String> args) {

        def result = []
        if( !args ) {
            result << this.getClass().getAnnotation(Parameters).commandDescription()
            result << 'Usage: nextflow secret <sub-command> [options]'
            result << ''
            result << 'Commands:'
            commands.collect{ it.name }.sort().each { result << "  $it".toString()  }
            result << ''
        }
        else {
            def sub = commands.find { it.name == args[0] }
            if( sub )
                sub.usage(result)
            else {
                throw new AbortOperationException("Unknown secret sub-command: ${args[0]}")
            }
        }

        println result.join('\n').toString()
    }

    /**
     * Main command entry point
     */
    @Override
    void run() {
        if( !args ) {
            usage()
            return
        }

        // setup the plugins system and load the secrets provider
        Plugins.setup()
        provider = SecretsLoader.instance.load()

        // run the command
        try {
            getCmd(args).apply()
        }
        finally {
            // close the provider
            provider?.close()
        }
    }

    protected SubCmd getCmd(List<String> args) {

        def cmd = commands.find { it.name == args[0] }
        if( cmd ) {
            return cmd
        }

        def matches = commands.collect{ it.name }.closest(args[0])
        def msg = "Unknown cloud sub-command: ${args[0]}"
        if( matches )
            msg += " -- Did you mean one of these?\n" + matches.collect { "  $it"}.join('\n')
        throw new AbortOperationException(msg)
    }

    private void addOption(String fieldName, List<String> result) {
        def annot = this.class.getDeclaredField(fieldName)?.getAnnotation(Parameter)
        if( annot ) {
            result << '  ' + annot.names().join(', ')
            result << '     ' + annot.description()
        }
        else {
            log.debug "Unknown help field: $fieldName"
        }
    }

    /**
     * Implements the secret `put` sub-command
     */
    class PutCmd implements SubCmd {

        @Override
        String getName() { 'put' }

        @Override
        void apply() {
            if( !secretName )
                throw new AbortOperationException("Missing secret name")
            if( !secretValue )
                throw new AbortOperationException("Missing secret value")
            provider.putSecret(secretName, secretValue)
        }

        @Override
        void usage(List<String> result) {
            result << 'Put a key-pair in the secret store'
            result << "Usage: nextflow secret $name -name <NAME> -value <VALUE>".toString()
            result << ''
            addOption('secretName', result)
            addOption('secretValue', result)
            result << ''
        }
    }

    class GetCmd implements SubCmd {

        @Override
        String getName() { 'get' }

        @Override
        void apply() {
            if( !secretName )
                throw new AbortOperationException("Missing secret name")
            println provider.getSecret(secretName)?.value
        }

        @Override
        void usage(List<String> result) {
            result << 'Get a secret value with the name'
            result << "Usage: nextflow secret $name -name <NAME>".toString()
            result << ''
            addOption('secretName', result)
            result << ''
        }
    }

    /**
     * Implements the secret `list` sub-command
     */
    class ListCmd implements SubCmd {
        @Override
        String getName() { 'list' }

        @Override
        void apply() {
            final names = new ArrayList(provider.listSecretNames()).sort()
            if( names ) {
                for( String it : names ) {
                    println it
                }
            }
            else {
                println "no secrets available"
            }
        }

        @Override
        void usage(List<String> result) {
            result << 'List all names in the secret store'
            result << "Usage: nextflow secret $name".toString()
            result << ''
        }
    }

    /**
     * Implements the secret `remove` sub-command
     */
    class DeleteCmd implements SubCmd {
        @Override
        String getName() { 'delete' }

        @Override
        void apply() {
            if( !secretName )
                throw new AbortOperationException("Missing secret name")
            provider.removeSecret(secretName)
        }

        @Override
        void usage(List<String> result) {
            result << 'Delete an entry from the secret store'
            result << "Usage: nextflow secret $name -name".toString()
            result << ''
            addOption('secretName', result)
            result << ''
        }
    }
}
