import com.mongodb.Mongo
import com.mongodb.gridfs.GridFS
import net.yajjl.JsonSerializer
import org.apache.shiro.SecurityUtils
import org.apache.shiro.authc.UsernamePasswordToken
import org.apache.shiro.authz.SimpleRole
import org.apache.shiro.authz.permission.WildcardPermission
import org.apache.shiro.mgt.DefaultSecurityManager
import org.apache.shiro.realm.SimpleAccountRealm

import static org.ratpackframework.groovy.RatpackScript.ratpack
import static org.ratpackframework.groovy.Template.groovyTemplate

def root
def mongo
def fs = [:]
def dbNames = ['general', 'photos', 'videos']
def serializer = new JsonSerializer()
def realm = new SimpleAccountRealm()
def securityManager = new DefaultSecurityManager(realm)
def NOT_LOGGED_IN = "You are not logged in."



def setupRoles = {
    def admin = new SimpleRole('Administrator')
    admin.add new WildcardPermission('data:upload')
    admin.add new WildcardPermission('data:delete')
    admin.add new WildcardPermission('data:view')
    admin.add new WildcardPermission('data:list')

    realm.add admin

    def guest = new SimpleRole('Guest')
    guest.add new WildcardPermission('data:view')

    realm.add guest

    realm.addAccount 'admin', 'CHANGEME', 'Administrator'

}

mongo = new Mongo("127.0.0.1", 27017)
for (name in dbNames) {
    fs.put name, new GridFS(mongo.getDB(name))
}
println fs
SecurityUtils.setSecurityManager(securityManager)
setupRoles()

def checkAuth = {
    def currentUser = SecurityUtils.getSubject()
    return currentUser.isAuthenticated()
}

def saveFile(file, fsLocation) {
    def byteData = file.get()
    def contentType = file.getContentType()
    def filename = file.getName()

    def gridfs = fs[fsLocation]
    try {
        if (gridfs.findOne(filename) == null) {
            save(byteData, contentType, gridfs, filename)
            return true
        } else {
            // Removing old file and replacing with new one
            gridfs.remove(filename)
            save(byteData, contentType, gridfs, filename)
            return true
        }
    } catch (Exception ex) {
        ex.printStackTrace()
    }
}

def saveFileAs(file, newFilename, fsLocation) {

}

def save = { byteData, contentType, f, filename ->
    def inputFile = f.createFile(byteData)
    inputFile.setContentType(contentType)
    inputFile.setFilename(filename)
    inputFile.save()
}

def retrieveFile = { filename, f ->
    def gridfs = fs[f]
    if (gridfs)
        return gridfs.findOne(filename)
}

def deleteFile = { filename, f ->
    def gridfs = fs[f]
    if (gridfs)
        gridfs.remove(filename)
}

def getFilesList = { f ->
    def gridfs = fs[f]
    def cursor = gridfs?.getFileList()
    cursor?.toArray()
}

def downloadFile = { filename, dbName, response ->
    def file = retrieveFile(filename, dbName)
    if (file != null) {
        println response
        response.send(file.getContentType(), file.getInputStream())
    } else "File not found"
}

def upload = {
    def f = request.getFile('myFile')
    println request
    println f
    if (!f.empty) {
        if (gridfsService.saveFile(f)) {
            redirect(action: 'uploadComplete')
        } else {
            flash.message = 'Error occured during upload, please try again.'
            redirect(action: 'uploadFile')
        }
    } else {
        flash.message = 'An empty file cannot be uploaded.'
        redirect(action: 'uploadFile')
    }
}

ratpack {
    handlers {

        get("login") {
            render groovyTemplate("login.html")
        }
        post("loginComplete") {
            def currentUser = SecurityUtils.getSubject()
            // todo make this actually secure
            def form = getRequest().getForm()
            def token = new UsernamePasswordToken(form.username, form.password)
            token.setRememberMe(true)
            try {
                currentUser.login(token)
                render groovyTemplate("folders.html", names: dbNames)
            } catch (Exception ex) {
                println ex.printStackTrace()
                "Wrong username or password."
            }
        }
        get('logout') {
            def currentUser = SecurityUtils.getSubject()
            currentUser.logout()
            "Logged out."
        }
        get("uploadFile/:dbName") {
            def folder = getPathTokens().dbName
            //if (checkAuth()) {
                //getResponse().send("fjfsdjkdhkf")
                render groovyTemplate("upload.html", folder: "general")
            //} else getResponse().send(NOT_LOGGED_IN)
        }

        get("file/:dbName/:filename") {
            downloadFile(getPathTokens().filename, getPathTokens().dbName, response)
        }

        get("deleteFile/:dbName/:filename") {
            if (checkAuth()) {
                deleteFile(getPathTokens().filename, getPathTokens().dbName)
                "file deleted"
                //render groovyTemplate("fileList.html", folderName: getPathTokens().dbName, files: list)
            } else NOT_LOGGED_IN
        }

        get("folder/:dbName") {
            if (checkAuth()) {
                def list = getFilesList(getPathTokens().dbName)
                if (list?.size() == 0)
                    render getResponse().send("No files in ${getPathTokens().dbName}")
                else if (getPathTokens().raw != null)
                    list.toString()
                else render groovyTemplate("fileList.html", folderName: getPathTokens().dbName, files: list)
            } else NOT_LOGGED_IN
        }


        post("postFile") {
            println "here"
            //if (checkAuth()) {
                println request.getForm().keySet()
            /*def nettyRequest = request.nettyRequest
            HttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE)
            HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(factory, nettyRequest)
            def chunk = (HttpChunk) nettyRequest;
            decoder.offer(chunk)
            Map <String, Object> multipartForm = [:]
            try {
                while (decoder.hasNext()) {
                    InterfaceHttpData data = decoder.next()
                    switch (data.httpDataType) {
                        case InterfaceHttpData.HttpDataType.Attribute:
                            Attribute attribute = (Attribute) data
                            multipartForm."${attribute.name}" = attribute.value
                            break
                        case InterfaceHttpData.HttpDataType.FileUpload:
                            FileUpload fileUpload = (FileUpload) data
                            if (fileUpload.completed) { // which is right because of the HttpObjectAggregator
                                File file = fileUpload.file
                                multipartForm."${data.name} (${file.name})" = file
                            }
                    }
                }
            } catch (HttpPostRequestDecoder.EndOfDataDecoderException e) {
                // This ugly empty catch is hit everytime and is therefore necessary
            }
            response.send(multipartForm.toString())*/

        //} else getResponse().send "hwew"
        }

        assets "public"

    }
}

