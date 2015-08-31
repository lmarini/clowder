package controllers

import java.net.URL
import java.util.Date
import javax.inject.Inject

import api.Permission
import models._
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient
import play.api.Logger
import play.api.data.{Forms, Form}
import play.api.mvc.MultipartFormData
import play.api.data.Forms._
import services._
import util.RequiredFieldsConfig
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.json.JsArray
import org.apache.http.client.methods.HttpPost

import scala.text

class CurationObjects @Inject()( curations: CurationService,
                                 datasets: DatasetService,
                                 collections: CollectionService,
                                 spaces: SpaceService,
                                 files: FileService,
                                 comments: CommentService,
                                 sections: SectionService
                               ) extends SecuredController {

  def newCO(datasetId:UUID, spaceId:String) = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.dataset, datasetId))) { implicit request =>
    implicit val user = request.user
    val (name, desc, spaceByDataset) = datasets.get(datasetId) match {
      case Some(dataset) => (dataset.name, dataset.description, dataset.spaces map( id => spaces.get(id).get) filter(_ != None) filter (space => Permission.checkPermission(Permission.EditStagingArea, ResourceRef(ResourceRef.space, space.id))))
      case None => ("", "", List.empty)
    }

    val defaultspace = spaceId match {
      case "" => None
      case _ => spaces.get(UUID(spaceId))
    }


    Ok(views.html.curations.newCuration(datasetId, name, desc, defaultspace, spaceByDataset, RequiredFieldsConfig.isNameRequired,
      RequiredFieldsConfig.isDescriptionRequired))
  }

  /**
   * Controller flow to create a new curation object. On success,
   * the browser is redirected to the new Curation page.
   */
  def submit(datasetId:UUID) = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.dataset, datasetId))) (parse.multipartFormData)  { implicit request =>

    //get name, des, space from request
    var COName = request.body.asFormUrlEncoded.getOrElse("name", null)
    var CODesc = request.body.asFormUrlEncoded.getOrElse("description", null)
    var COSpace = request.body.asFormUrlEncoded.getOrElse("space", null)

    implicit val user = request.user
    user match {
      case Some(identity) => {
        //TODO:check COName is null

        datasets.get(datasetId) match {
          case Some(dataset) => {
            val spaceId = UUID(COSpace(0))
            if(Permission.checkPermission(Permission.EditStagingArea, ResourceRef(ResourceRef.space, spaceId))){
            //the model of CO have multiple datasets and collections, here we insert a list containing one dataset
            val newCuration = CurationObject(
              name = COName(0),
              author = identity,
              description = CODesc(0),
              created = new Date,
              space = spaceId,
              datasets = List(dataset)
            )

            // insert curation
            Logger.debug("create Co: " + newCuration.id)
            curations.insert(newCuration)
            spaces.addCurationObject(spaceId, newCuration.id)
            Redirect(routes.CurationObjects.getCurationObject(spaceId, newCuration.id))
          }
              else InternalServerError("Permission Denied")
          }
          case None => InternalServerError("Dataset Not found")
        }
      }
      case None => InternalServerError("User Not found")
    }
  }

  def getCurationObject(spaceId: UUID, curationId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.space, spaceId))) {
    implicit request =>
      implicit val user = request.user
      spaces.get(spaceId) match {
        case Some(s) => {
          curations.get(curationId) match {
            case Some(c) => {
              val ds: Dataset = c.datasets(0)
              val dsmetadata = datasets.getMetadata(ds.id)
              val dsUsrMetadata = datasets.getUserMetadata(ds.id)
              val isRDFExportEnabled = current.plugin[RDFExportService].isDefined
              val filesUsrMetadata: Map[String, scala.collection.mutable.Map[String,Any]] = ds.files.map(file=> file.id.stringify -> files.getUserMetadata(file.id)).toMap.asInstanceOf[Map[String, scala.collection.mutable.Map[String,Any]]]
              Ok(views.html.spaces.curationObject(s, c, dsmetadata, dsUsrMetadata, filesUsrMetadata, isRDFExportEnabled))
            }
            case None => InternalServerError("Curation Object Not found")
          }
        }
        case None => InternalServerError("Space not found")

      }

  }

  def findMatchingRepositories(spaceId: UUID, curationId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.space, spaceId))) {
    implicit request =>
      implicit val user = request.user
      spaces.get(spaceId) match {
        case Some(s) => {
          curations.get(curationId) match {
            case Some(c) => {
              //TODO: Make some sort of call to the matckmaker
              val propertiesMap: Map[String, List[String]] = Map( "Access" -> List("Open", "Restricted", "Embargo", "Enclave"),
                "License" -> List("Creative Commons", "GPL") , "Cost" -> List("Free", "$XX Fee"),
              "Organizational Affiliation" -> List("UMich", "IU", "UIUC"))

              Ok(views.html.spaces.matchmakerResult(s, c, propertiesMap))
            }
            case None => InternalServerError("Curation Object not found")
          }
        }
        case None => InternalServerError("Space not found")
      }

  }

  def compareToRepository(spaceId: UUID, curationId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.space, spaceId))) {
    implicit request =>
      implicit val user = request.user
      spaces.get(spaceId) match {
        case Some(s) => {
         curations.get(curationId) match {
           case Some(c) => {
             //TODO: Make some call to C3-PR?
           //  Ok(views.html.spaces.matchmakerReport())
             val propertiesMap: Map[String, List[String]] = Map("Content Types" -> List("Images", "Video"),
               "Dissemination Control" -> List("Restricted Use", "Ability to Embargo"),"License" -> List("Creative Commons", "GPL") ,
               "Organizational Affiliation" -> List("UMich", "IU", "UIUC"))

             Ok(views.html.spaces.curationDetailReport(s, c, propertiesMap))
           }
           case None => InternalServerError("Curation Object not found")

         }
        }
        case None => InternalServerError("Space not found")
      }
  }

  def sendToRepository(spaceId: UUID, curationId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.space, spaceId))) {
    implicit request =>
      implicit val user = request.user
      spaces.get(spaceId) match {
        case Some(s) => {
          curations.get(curationId) match {
            case Some(c) =>
              //TODO : Submit the curationId to the repository. This probably needs the repository as input
              var success = false
              val hostIp = Utils.baseUrl(request)
              val hostUrl = hostIp + "/api/curations/" + curationId + "/ore"
              val valuetoSend = Json.toJson(
                Map(
                  "Repository" -> Json.toJson("Ideals"),
                  "Preferences" -> Json.toJson(
                    Map(
                      "key1" -> Json.toJson("val1"),
                      "key2" -> Json.toJson("val2")
                    ))
                  ,
                  "Aggregation" -> Json.toJson (
                    Map(
                      "Identifier" -> Json.toJson(curationId),
                      "@id" -> Json.toJson(hostUrl),
                      "Title" -> Json.toJson(c.name)
                    )
                  )
                )

                )

              var endpoint = "https://sead-test.ncsa.illinois.edu/sead-cp/cp/researchobjects"
              val httpPost = new HttpPost(endpoint)
              httpPost.setHeader("Content-Type", "application/json")
              httpPost.setEntity(new StringEntity(Json.stringify(valuetoSend)))
              var client = new DefaultHttpClient
              val response = client.execute(httpPost)
              val responseStatus = response.getStatusLine().getStatusCode()
              if(responseStatus >= 200 && responseStatus < 300 || responseStatus == 304) {
                curations.setSubmitted(c.id, true)
                success = true
              }

              Ok(views.html.spaces.curationSubmitted(s, c, "IDEALS", success))
          }
        }
        case None => InternalServerError("Space Not found")
      }
  }


}



