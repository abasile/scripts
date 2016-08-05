
/****************************************************************************
 * Pushover
 * 				https://pushover.net
 ****************************************************************************/
def Pushover(user, token) {
	new PushoverClient(user:user, token:token)
}

class PushoverClient {
	def user
	def token

	def endpoint = new URL('https://api.pushover.net/1/messages.xml')
	def titleLimit = 100
	def messageLimit = 512

	def send = { title, message ->
		def lines = message.readLines().collect{ it + '\n' }
		def pages = []

		def currentPage = ''
		lines.each{ line ->
			if (currentPage.length() + line.length() >= messageLimit) {
				pages << currentPage
				currentPage = ''
			}
			currentPage += line
		}
		pages << currentPage

		// use title and make space for pagination
		def pageTitle = (title as String)

		// submit post requests
		pages = pages.findAll{ it.length() > 0 }

		pages.eachWithIndex(){ m, i ->
			if (i > 0) sleep(1000) // max 1 request / sec
			def t = pages.size() <= 1 ? pageTitle : "${pageTitle} (${i+1}/${pages.size()})"
			post(m, [title: t])
		}
	}

	def post = { text, parameters = [:] ->
		// inject default post parameters
		parameters << [token:token, user:user, message:text as String]

		// post and process response
		endpoint.post(parameters, [:])
	}
}



/****************************************************************************
 * PushBullet
 * 				https://www.pushbullet.com
 ****************************************************************************/
def PushBullet(apikey, devices = null) {
    new PushBulletClient2(apikey:apikey, devices:devices)
}

class PushBulletClient {
	def apikey

	def endpoint_pushes = new URL('https://api.pushbullet.com/v2/pushes')
	def endpoint_upload = new URL('https://api.pushbullet.com/v2/upload-request')

	def sendFile = { file_name, file_content, file_type, body = null, email = null ->
		def requestProperties = [Authorization: 'Basic '+apikey.getBytes().encodeBase64()]
		
		// prepare upload
		def uploadRequestData = [file_name: file_name, file_type: file_type]
		def response = new JsonSlurper().parseText(endpoint_upload.post(uploadRequestData, requestProperties).text)
		
		// build multipart/form-data -- the most horrible data format that we will never get rid off :(
		def MBD = '--'		// random -- before the boundry, or after, or not at all (there goes 4 hours of my life)
		def EOL = '\r\n'	// CR+NL required per spec?! I shit you not!

		def multiPartFormBoundary = '----------NCC-1701-E'
		def multiPartFormType = 'multipart/form-data; boundary='+multiPartFormBoundary
		def multiPartFormData = new ByteArrayOutputStream()

		multiPartFormData.withWriter('UTF-8') { out ->
			response.data.each{ key, value -> 
				out << MBD << multiPartFormBoundary << EOL
				out << 'Content-Disposition: form-data; name="' << key << '"' << EOL << EOL
				out << value << EOL
			}
			out << MBD << multiPartFormBoundary << EOL
			out << 'Content-Disposition: form-data; name="file"; filename="' << file_name << '"' << EOL
			out << 'Content-Type: ' << file_type << EOL << EOL
			out << file_content << EOL
			out << MBD << multiPartFormBoundary << MBD << EOL
		}

		// do upload to Amazon S3
		new URL(response.upload_url).post(multiPartFormData.toByteArray(), multiPartFormType, ['Content-Encoding':'gzip'])

		// push file link
		def pushFileData = [type: 'file', file_url: response.file_url, file_name: file_name, file_type: file_type, body: body, email: email]
		endpoint_pushes.post(pushFileData, requestProperties)
	}
}

class PushBulletClient2 {
    def apikey
    def devices

    def url_devices = new URL('https://api.pushbullet.com/v2/devices')
    def url_pushes = new URL('https://api.pushbullet.com/v2/pushes')
    def url_upload = new URL('https://api.pushbullet.com/v2/upload-request')

    def sendHtml = { title, FileContent, textMessage = "" ->
        def auth = "${apikey}".getBytes().encodeBase64().toString()
        def header = [requestProperties: [Authorization: "Basic ${auth}" as String]]
        def response = new groovy.json.JsonSlurper().parse(url_devices, header)
        def targets = devices? response.devices.findAll{it.nickname =~ devices || it.iden =~ devices}.findResults{ it.iden } :['']

        targets.each{ device_iden ->
            def decoder = java.nio.charset.Charset.forName("UTF-8").newDecoder()
            def uploadRequestResponse = new groovy.json.JsonSlurper().parseText(
                    decoder.decode(url_upload.post([file_name:"${title}.html",file_type:"text/html"],header.requestProperties)).toString()
            )
            def contentType = 'multipart/form-data; boundary=----------------------------a1134e1059ac'
            def multiPartFormData = """------------------------------a1134e1059ac
"""
            uploadRequestResponse.data.each{name , value ->
                multiPartFormData += """Content-Disposition: form-data; name="${name}"

${value}
------------------------------a1134e1059ac
"""
            }
            multiPartFormData += """Content-Disposition: form-data; name="file"

${FileContent}
------------------------------a1134e1059ac--
"""

            println "reponse upload request ${uploadRequestResponse}"
            new URL(uploadRequestResponse.upload_url).post(multiPartFormData.replaceAll("\\r?\\n","\r\n").getBytes("UTF-8"), contentType, [:])
            url_pushes.post([type:"link",device_iden:device_iden,title:title,url:uploadRequestResponse.file_url,body:textMessage],header.requestProperties)
        }
    }
}

def Utorrent(host, port, id, pwd, log){
    new Utorrent(host: host, port: port, id: id, password: pwd, lg: log)
}

// uTorrent class
public class Utorrent {
    def host = "localhost"
    def port = 80
    def id, password
    def token, cookie, lg

    /**
     * Fetch the authentication token from uTorrent and store it in the object instance
     */
    protected void fetchToken() {
        lg.fine("Fetching uTorrent token...")
        String tok = get("/gui/token.html", [:], true)
        def m = tok =~ /<div .*>(.+)<\/div>/
        token = m[0][1]
    }

    protected void fetchCookie(URLConnection conn) {
        def headerName = null
        for (int i=1; (headerName = conn.getHeaderFieldKey(i)) != null; i++) {
            if (headerName.equalsIgnoreCase("Set-Cookie")) {
                String cook = conn.getHeaderField(i)
                cookie = cook.substring(0, cook.indexOf(";"))
            }
        }
    }

    /**
     * Perform a command against the web UI
     * @param cmd The command to perform; the resource portion of the URL (i.e. /gui/)
     * @param args The map of key/val query args to be appended to the URL; do NOT url-encode the values ahead of time
     * @param isTok Prevents infinite loops; set to true when trying to grab the auth token from uTorrent server
     * @return The result of the command as received from uTorrent server
     */
    protected String get(def cmd, def args, def isTok = false) {
        if(token == null && !isTok)
            fetchToken()
        def url = "http://$host:$port$cmd?" + (token != null ? "token=" + java.net.URLEncoder.encode(token, "UTF-8") : "")
        args.each {k, v ->
            url += "&" + java.net.URLEncoder.encode(k) + "=" + java.net.URLEncoder.encode(v)
        }
        def conn = new URL(url).openConnection()
        if(id != "" || password != "")
            conn.setRequestProperty("Authorization", "Basic " + "$id:$password".getBytes().encodeBase64().toString())
        if(!isTok && cookie != "")
            conn.setRequestProperty("Cookie", cookie.toString())
        else
            fetchCookie(conn)
        def out = new ByteArrayOutputStream()
        out << conn.getInputStream()
        return out.toString()
    }

    protected void stop(String hash) {
        get("/gui/", ["action": "stop", "hash": hash])
    }

    protected void start(String hash) {
        // first rechech
        get("/gui/", ["action": "recheck", "hash": hash])
        // than start
        get("/gui/", ["action": "start", "hash": hash])
    }


    protected void removedata(String hash) {
        // remove torrent and data
        get("/gui/", ["action": "removedata", "hash": hash])
    }



    /**
     * Add a new torrent URL to be downloaded
     * @param url The url of the torrent file to be downloaded by uTorrent
     */
    public void addUrl(def url) {
        def resource = url.toString()
        get("/gui/", ["action":"add-url", "s":resource])
    }

}
