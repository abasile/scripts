/**
 * Created by a on 01/09/2014.
 */

include('lib/web')

def findSubTest = { missingSubFile , notFoundMissingSubFile->
    def json  = new groovy.json.JsonSlurper().parseText((new File(missingSubFile).text)?:"[]")
    def notFound = []
    def rejected = []
    json.each{ video ->
        def currentFile = new File("$video.dir\\\\$video.file")
        def oldFile = new File("$video.dir\\$video.original")
        println "$currentFile.path  -->  $oldFile.path"
        currentFile.renameTo(oldFile)
        def subtitleFiles = getMissingSubtitles(file:[oldFile], lang:'en', strict:false, output:'srt', encoding:'UTF-8', db: 'OpenSubtitles', format:'MATCH_VIDEO_ADD_LANGUAGE_TAG') ?: []
        def sub = subtitleFiles.size()>0?subtitleFiles[0] : null
        if (sub) {sub.renameTo("$sub.dir\\${currentFile.nameWithoutExtension}.eng.$sub.extension")}
        else{(System.currentTimeMillis() - video.date < 10*24*3600*1000)? notFound.add(video) : rejected.add(video)}
        oldFile.renameTo(currentFile)
    }

    def builder  = new groovy.json.JsonBuilder()
    builder.call(notFound)
    new File(missingSubFile).write(builder.toPrettyString())

    def rejectedFile = new groovy.json.JsonSlurper().parseText((new File(notFoundMissingSubFile).text)?:"[]")
    builder.call(rejectedFile+rejected)
    new File(notFoundMissingSubFile).write(builder.toPrettyString())
}

findSubTest("E:\\downloadchaine\\withoutSubList.json","E:\\downloadchaine\\subNotFoundList.json")


