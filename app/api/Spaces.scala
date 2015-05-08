package api

import java.util.Date
import javax.inject.Inject
import com.wordnik.swagger.annotations.{ApiOperation, Api}
import models.{UUID, ProjectSpace}
import play.api.Logger
import controllers.Utils
import play.api.Play._
import play.api.libs.json.Json
import play.api.libs.json.Json._
import services.{AdminsNotifierPlugin, SpaceService}
import play.api.libs.json.JsResult
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsError
import scala.collection.mutable.ListBuffer
import models.Role

/**
 * Spaces allow users to partition the data into realms only accessible to users with the right permissions.
 *
 * @author Luigi Marini
 * @author Rob Kooper
 * @author Jong Lee
 *
 */
@Api(value = "/spaces", listingPath = "/api-docs.json/spaces", description = "Spaces are groupings of collections and datasets.")
class Spaces @Inject()(spaces: SpaceService) extends ApiController {

  @ApiOperation(value = "Create a space",
    notes = "",
    responseClass = "None", httpMethod = "POST")
  //TODO- Minimal Space created with Name and description. URLs are not yet put in
  def createSpace() = SecuredAction(authorization = WithPermission(Permission.CreateSpaces)) {
    request =>
      Logger.debug("Creating new space")
      val nameOpt = (request.body \ "name").asOpt[String]
      val descOpt = (request.body \ "description").asOpt[String]
      (nameOpt, descOpt) match{
        case(Some(name), Some(description)) =>{
          // TODO: add creator
          val userId = request.mediciUser.fold(UUID.generate)(_.id)
          val c = ProjectSpace(name = name, description = description, created = new Date(), creator = userId,
            homePage = List.empty, logoURL = None, bannerURL = None, collectionCount=0,
            datasetCount=0, userCount=0, metadata=List.empty)
          spaces.insert(c) match {
            case Some(id) => {
              Ok(toJson(Map("id" -> id)))
            }
            case None => Ok(toJson(Map("status" -> "error")))
          }

        }
        case (_, _) => BadRequest(toJson("Missing required parameters"))
      }
  }

  @ApiOperation(value = "Remove a space",
    notes = "Does not delete the individual datasets and collections in the space.",
    responseClass = "None", httpMethod = "DELETE")
  def removeSpace(spaceId: UUID) = SecuredAction(parse.anyContent,
    authorization = WithPermission(Permission.DeleteSpaces), resourceId = Some(spaceId)) { request =>
    spaces.get(spaceId) match {
      case Some(space) => {
        spaces.delete(spaceId)
        current.plugin[AdminsNotifierPlugin].foreach {
          _.sendAdminsNotification(Utils.baseUrl(request), "Space", "removed", space.id.stringify, space.name)
        }
      }
    }
    //Success anyway, as if space is not found it is most probably deleted already
    Ok(toJson(Map("status" -> "success")))
  }

  @ApiOperation(value = "Get a space",
    notes = "Retrieves information about a space",
    responseClass = "None", httpMethod = "GET")
  def get(id: UUID) = SecuredAction(parse.anyContent,
    authorization = WithPermission(Permission.ShowSpace), resourceId = Some(id)) { request =>
    spaces.get(id) match {
      case Some(space) => Ok(spaceToJson(Utils.decodeSpaceElements(space)))
      case None => BadRequest("Space not found")
    }
  }

  @ApiOperation(value = "List spaces",
    notes = "Retrieves information about spaces",
    responseClass = "None", httpMethod = "GET")
  def list() = SecuredAction(parse.anyContent,
    authorization = WithPermission(Permission.ListSpaces)) { request => {
        var decodedSpaceList = new ListBuffer[models.ProjectSpace]()
	    for (aSpace <- spaces.list()) {
	        decodedSpaceList += Utils.decodeSpaceElements(aSpace)
	    }
    	Ok(toJson(decodedSpaceList.toList.map(spaceToJson)))
    }
  }

  def spaceToJson(space: ProjectSpace) = {
    toJson(Map("id" -> space.id.stringify,
      "name" -> space.name,
      "description" -> space.description,
      "created" -> space.created.toString))
  }

  @ApiOperation(value = "Associate a collection with a space",
    notes = "",
    responseClass = "None", httpMethod = "POST")
  def addCollection(space: UUID) = SecuredAction(parse.json,
    authorization = WithPermission(Permission.EditCollection)) { request =>
    val collectionId = (request.body \ "collection_id").as[String]
    spaces.addCollection(UUID(collectionId), space)
    Ok(toJson("success"))
  }

  @ApiOperation(value = "Associate a dataset with a space",
    notes = "",
    responseClass = "None", httpMethod = "POST")
  def addDataset(space: UUID) = SecuredAction(parse.json,
    authorization = WithPermission(Permission.EditCollection)) { request =>
    val datasetId = (request.body \ "dataset_id").as[String]
    spaces.addDataset(UUID(datasetId), space)
    Ok(toJson("success"))
  }
  
  /**
   * REST endpoint: POST call to update the configuration information associated with a specific Space
   * 
   *  Takes one arg, id:
   *  
   *  id, the UUID associated with the space that will be updated 
   *  
   *  The data contained in the request body will defined by the following String key-value pairs:
   *  
   *  description -> The text for the updated description for the space
   *  name -> The text for the updated name for this space
   *  timetolive -> Text that represents an integer for the number of hours to retain resources
   *  enabled -> Text that represents a boolean flag for whether or not the space should purge resources that have expired
   *  
   */
  @ApiOperation(value = "Update the information associated with a space", notes="",
    responseClass = "None", httpMethod = "POST")
  def updateSpace(spaceid: UUID) = SecuredAction(parse.json, authorization = WithPermission(Permission.UpdateSpaces))
  { request =>
      if (UUID.isValid(spaceid.stringify)) {          

          //Set up the vars we are looking for
          var description: String = null
          var name: String = null
          var timeAsString: String = null
          var enabled: Boolean = false;
          
          var aResult: JsResult[String] = (request.body \ "description").validate[String]
          
          // Pattern matching
          aResult match {
              case s: JsSuccess[String] => {
                description = s.get
              }
              case e: JsError => {
                Logger.error("Errors: " + JsError.toFlatJson(e).toString())
                BadRequest(toJson("description data is missing from the updateSpace call."))
              }                            
          }
          
          aResult = (request.body \ "name").validate[String]
          
          // Pattern matching
          aResult match {
              case s: JsSuccess[String] => {
                name = s.get
              }
              case e: JsError => {
                Logger.error("Errors: " + JsError.toFlatJson(e).toString())
                BadRequest(toJson("name data is missing from the updateSpace call."))
              }                            
          }
          
          aResult = (request.body \ "timetolive").validate[String]
          
          // Pattern matching
          aResult match {
              case s: JsSuccess[String] => {
                timeAsString = s.get
              }
              case e: JsError => {
                Logger.error("Errors: " + JsError.toFlatJson(e).toString())
                BadRequest(toJson("timetolive data is missing from the updateSpace call."))
              }                            
          }
          
          var boolResult = (request.body \ "enabled").validate[Boolean]
          
          // Pattern matching
          boolResult match {
              case b: JsSuccess[Boolean] => {
                enabled = b.get
              }
              case e: JsError => {
                Logger.error("Errors: " + JsError.toFlatJson(e).toString())
                BadRequest(toJson("enabled data is missing from the updateSpace call."))
              }                            
          }
          
          Logger.debug(s"updateInformation for dataset with id  $spaceid. Args are $description, $name, $enabled, and $timeAsString")
          
          //Generate the expiration time and the boolean flag
          val timeToLive =  timeAsString.toInt*60*60*1000L
          //val expireEnabled = enabledAsString.toBoolean
          Logger.debug("converted values are " + timeToLive + " and " + enabled)
          
          spaces.updateSpaceConfiguration(spaceid, name, description, timeToLive, enabled)
          Ok(Json.obj("status" -> "success"))
      } 
      else {
        Logger.error(s"The given id $spaceid is not a valid ObjectId.")
        BadRequest(toJson(s"The given id $spaceid is not a valid ObjectId."))
      }
  }
  
   /**
   * REST endpoint: POST call to update the user information associated with a specific Space
   * 
   *  Takes one arg, id:
   *  
   *  id, the UUID associated with the space that will be updated 
   *  
   *  The data contained in the request body will defined by the following String key-value pairs:
   *  
   *  rolesandusers -> A map that contains a role level as a key and a comma separated String of user IDs as the value
   *  
   */
  @ApiOperation(value = "Update the information associated with a space", notes="",
    responseClass = "None", httpMethod = "POST")
  def updateUsers(spaceid: UUID) = SecuredAction(parse.json, authorization = WithPermission(Permission.UpdateSpaces))
  { request =>
      if (UUID.isValid(spaceid.stringify)) {
           var aResult: JsResult[Map[String, String]] = (request.body \ "rolesandusers").validate[Map[String, String]]
          
          // Pattern matching
          aResult match {
              case aMap: JsSuccess[Map[String, String]] => {
				//Set up a map of existing users to check against
                val existingUsers = spaces.getUsersInSpace(spaceid)
                var existUserRole: Map[String, String] = Map.empty
                for (aUser <- existingUsers) {
                    spaces.getRoleForUserInSpace(spaceid, aUser.id) match {
                        case Some(aRole) => {
                        	existUserRole += (aUser.id.stringify -> aRole.name)
                        }
                        case None => Logger.debug("--- Odd, user in space with no role...")
                    }
                    
                }
				 
                Logger.debug("---- existUserRole map is " + existUserRole)
				Logger.debug("------ Found, aMap is " + aMap)
				var roleMap: Map[String, String] = aMap.get
				for ((k, v) <- roleMap) {
				    Logger.debug("key is " + k + " and value is " + v)
				    //The role needs to exist
				    Role.roleMap.get(k) match {
				        case Some (aRole) => {
				            var idArray: Array[String] = v.split(",").map(_.trim())
				            Logger.debug("---- idArray length is " + idArray.length)
					        				            
				            //Deal with all the ids that were sent up (changes and adds)				            
						    for (aUserId <- idArray) {
						        Logger.debug("aUser Id is -"+aUserId+"-")
						        //For some reason, an empty string is getting through as aUserId on length
						        if (aUserId != "") {								        
							        if (existUserRole.contains(aUserId)) {
							            //The user exists in the space alread							            
							            existUserRole.get(aUserId) match { 
							                case Some(existRole) => {
									            if (existRole != k) {
									                Logger.debug("---- contains, changing")
									                spaces.changeUserRole(UUID(aUserId), aRole, spaceid)
									            }
									            else {
									                //Same level, so no change
									                Logger.debug("---- contains, no change")
									            }
							                }
							                case None => Logger.debug("---- no role associated for some reason")
							            }
							        }							            
							        else {
							            //New user completely to the space
						                Logger.debug("---- new, adding")
						                spaces.addUser(UUID(aUserId), aRole, spaceid)
						            }							        
						        }
						        else {
					                Logger.debug("---- There was an empty string that counted as an array...")
					            }
						    }
				            				            
				            
				            //Deal with users that were removed
				            for (existUserId <- existUserRole.keySet) {
				                if (!idArray.contains(existUserId)) {
				                    //Check if the role is for this level
				                    existUserRole.get(existUserId) match {
				                        case Some(existRole) => {
				                            if (existRole == k) {
				                                //In this case, the level is correct, so it is a removal
						                        Logger.debug("---- missing, removing")
							                    spaces.removeUser(UUID(existUserId), spaceid)
				                            }
				                        }
				                        case None => Logger.debug("--- This should never happen. Clean these up")
				                    }			                    
				                }
				            }				            
				        }
				        case None => Logger.debug("---- A role was sent up that doesn't exist.")
				    }				    
				}
				Ok(Json.obj("status" -> "success"))
              }
              case e: JsError => {
                Logger.error("Errors: " + JsError.toFlatJson(e).toString())
                BadRequest(toJson("rolesandusers data is missing from the updateUsers call."))
              }                            
          }
      }
      else {
        Logger.error(s"The given id $spaceid is not a valid ObjectId.")
        BadRequest(toJson(s"The given id $spaceid is not a valid ObjectId."))
      }
  }
          
}
