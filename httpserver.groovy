/*
 * Very simple HTTP server using http://sparkjava.com/
 *
 * Start the server with "groovy server.groovy".
 */

import groovy.json.JsonSlurper
import groovy.transform.SourceURI

@SourceURI
URI sourceUri

@Grab( group = 'com.sparkjava', module = 'spark-core', version = '2.1' )
import static spark.Spark.*

staticFileLocation '.'
port(8004)
get '/', { req, res -> log req; 'test filebot' }
post '/process', { req, res -> log req; parseReq req; "Thank you for sending me data: ${req.body()}" }

addShutdownHook {
    println ''
    println 'Script is ended.'
}

def log( req ) {
    println "Handling ${req.requestMethod()} ${req.pathInfo()}"
    println "Headers: ${req.headers().collect { it + ': ' + req.headers( it ) }}"
    def b
    if ( ( b = req.body() ) ) {
        println "Body: $b"
    } else {
        println "No body"
    }
}

def parseReq( req ) {
    println "Handling ${req.requestMethod()} ${req.pathInfo()}"
    println "Headers: ${req.headers().collect { it + ': ' + req.headers( it ) }}"
    def b
    if ( ( b = req.body() ) ) {
        def json = new JsonSlurper().parseText(b as String)
        println json
        def dir =  json["dir"]
        def file =  json["file"]
        def title =  json["title"]
        def hash =  json["hash"]
        println _args.args
        def movie_format = '''films/{n}({y})/{n.replaceTrailingBrackets().space('.')}{t.replaceAll(/[!?.]+$/) .lowerTrail().replacePart(', Part $1').space('.')}{'-'+fn.match(/(?i)French|fr|multi(?i)/).upper()}{'-'+vf.match(/720[pP]|1080[pP]/)}{'.'+source}{'.'+vc}{'-'+group}{'.'+lang}'''
        def serie_format = '''TVShows/{n}({y}){'/Season'+s.pad(2)}/{n.replaceTrailingBrackets().space('.')}-{s00e00}_{t.replaceAll(/[!?.]+$/) .lowerTrail().replacePart(', Part $1').space('.')}{'-'+fn.match(/(?i)French|fr|multi(?i)/).upper()}{'-'+vf.match(/720[pP]|1080[pP]/)}{'.'+source}{'.'+vc}{'-'+group}{'.'+lang}'''
        def amc_args = [
                "--action", "hardlink",
//                "--action", "test",
                "--output", "/medias/videos",
                "--log-file", "/config/amc.log",
                "--conflict", "override",
                "--encoding", "utf8",
                "-non-strict",
                "--def", "music=n",
                "subtitles=en",
                "artwork=y",
                "missingSubtitleList=/config/withoutSubList.json",
                "clean=y",
                "mailto=tictactic1+pushbullet@gmail.com",
                "plex=127.0.0.1:B37GVuPbVWbJqCfEgzeY",
//          "pushbullet=s095DFqBN4Pp3ltO9XL7eUVD5cZ0tfYK",
//          'pushbulletDevices="HTC 10"',
                "ut_dir=$dir",
                "ut_file=$file",
                "ut_kind=multi",
                "ut_title=$title",
                "ut_label=auto",
//                "ut_state=''",
                "ut_hash=$hash",
//          "ut_port={8}",
//          "ut_usr={9}",
//          "ut_pwd={10}",
                "seriesFormat=${serie_format}",
                "movieFormat=${movie_format}"
        ]

        def bean = new net.filebot.cli.ArgumentBean(*amc_args)
        println amc_args
        println "==script"
        try{
            executeScript("amc", java.util.Arrays.asList(bean.getArgumentArray()), bean.defines, [])
            println "done"
        }
        catch (Exception e)
        {
           println "error ${e.message}"
        }
        catch (net.filebot.cli.ScriptDeath d)
        {
            println "death ${d.message}"
        }
    } else {
        println "No body"
    }
}

println 'Enter to exit'
//def line  = System.in.newReader().readLine()
int i = 0
while (true)
{
    sleep(10000)
    i++
}
println "Done : ${i.toString()}"
stop()
