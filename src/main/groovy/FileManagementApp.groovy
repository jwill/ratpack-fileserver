import net.yajjl.*
import com.mongodb.Mongo
import com.mongodb.gridfs.GridFS
import com.bleedingwolf.ratpack.*
import org.apache.commons.fileupload.*
import org.apache.commons.fileupload.servlet.*
import org.apache.commons.fileupload.disk.*

import org.apache.shiro.realm.* 
import org.apache.shiro.authc.* 
import org.apache.shiro.authz.* 
import org.apache.shiro.subject.* 
import org.apache.shiro.SecurityUtils 
import org.apache.shiro.mgt.DefaultSecurityManager 
import org.apache.shiro.authz.permission.WildcardPermission 

public class FileManagementApp {
	def root
	def mongo
	def fs = [:]
	def dbNames = ['general', 'photos', 'videos']
	def serializer = new JsonSerializer()
	def realm = new SimpleAccountRealm()
	def securityManager = new DefaultSecurityManager(realm)
	def NOT_LOGGED_IN = "You are not logged in."

	def app = Ratpack.app {
		set 'public', 'public'
		set 'templateRoot', 'templates'
			
		get("/login") {
			render "login.html"
		}

		post("/login") {
			def currentUser = SecurityUtils.getSubject()
			// todo make this actually secure
			def token = new UsernamePasswordToken(params.username, params.password)
			token.setRememberMe(true)
			try {
				currentUser.login(token)
				render "files.html", [names: dbNames]
			} catch(Exception ex) {
				println ex.printStackTrace()
				"Wrong username or password."
			}
		}	

		get('/logout'){
			def currentUser = SecurityUtils.getSubject()
			currentUser.logout()
			"Logged out."
		}
		
		get("/uploadFile/:dbName") {
			if (checkAuth()) {
				render "upload.html", [folder:urlparams.dbName]
			} else NOT_LOGGED_IN
		}


		get("/file/:dbName/:filename") {
			downloadFile(urlparams.filename, urlparams.dbName, response)
		}

		get("/deleteFile/:dbName/:filename") {	
			if (checkAuth()) {
				deleteFile(urlparams.filename, urlparams.dbName)
				"file deleted"
			} else NOT_LOGGED_IN
		}

		get("/:dbName") {
			if (checkAuth()) {
					def list = getFilesList(urlparams.dbName)
					if (list?.size() == 0)
							return "No files in ${urlparams.dbName}"
					else if(params.raw != null)
							list.toString()
					else render "fileList.html", [folderName:urlparams.dbName, files:list]
		 	} else NOT_LOGGED_IN
		}

		
		post("/uploadFile") {
			if (checkAuth()) {
					def isMultipart = ServletFileUpload.isMultipartContent(request)
					def factory = new DiskFileItemFactory()
					def upload = new ServletFileUpload(factory)
					def items = upload.parseRequest(request)
					def uploaded = []
					for (item in items) {
						if( !item.isFormField()){
							def data = item.get()
							saveFile(item, params.folder)
							uploaded.add(item.getName())
						}
					}
					"Uploaded files: ${uploaded}"
			} else NOT_LOGGED_IN	
		}
	}

	def checkAuth = {
			def currentUser = SecurityUtils.getSubject()
			return currentUser.isAuthenticated()
				
	}

	public FileManagementApp() {
		mongo = new Mongo("localhost",27017)
		for (name in dbNames) {
			fs.put name, new GridFS(mongo.getDB(name))
		}
		println fs
		SecurityUtils.setSecurityManager(securityManager)
		setupRoles()
		RatpackServlet.serve(app)
	}

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

		realm.addAccount 'admin', 'Jcaue3Jb', 'Administrator'
		
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
			}
			else {
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

	def save = { byteData, contentType, fs, filename ->
		def inputFile = fs.createFile(byteData)
		inputFile.setContentType(contentType)
		inputFile.setFilename(filename)
		inputFile.save()
	}

	def retrieveFile = {filename, f ->
		def gridfs = fs[f]
		if (gridfs)
				return gridfs.findOne(filename)
	}

	def deleteFile = {filename, f ->
		def gridfs = fs[f]
		if (gridfs)
				gridfs.remove(filename)
	}

	def getFilesList = {f ->
		def gridfs = fs[f]
		def cursor = gridfs?.getFileList()
		cursor?.toArray()
	}

	def downloadFile = {filename, dbName, response ->
		def file = retrieveFile(filename, dbName)
    if (file != null) {
        response.outputStream << file.getInputStream()
        response.contentType = file.getContentType()
    } else "File not found"
	}

	def upload = {
    def f = request.getFile('myFile')
    println f
    if (!f.empty) {
        if (gridfsService.saveFile(f)) {
            redirect(action:'uploadComplete')
        } else {
            
            flash.message = 'Error occured during upload, please try again.'
            redirect(action:'uploadFile')
        }
    } else {
        flash.message = 'An empty file cannot be uploaded.'
        redirect(action:'uploadFile')
    }
	}
	

	public static void main(String[] args) {
		new FileManagementApp()
	}

	} 
