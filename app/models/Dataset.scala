package models

import com.mongodb.casbah.Imports._
import java.util.Date
import play.api.libs.json.{Writes, Json}
import play.api.libs.json._
import play.api.libs.functional.syntax._

/**
 * A dataset is a collection of files, and streams.
 */
case class Dataset(
  id: UUID = UUID.generate,
  name: String = "N/A",
  author: MiniUser,
  description: String = "N/A",
  created: Date,
  files: List[UUID] = List.empty,
  folders: List[UUID] = List.empty,
  streams_id: List[ObjectId] = List.empty,
  tags: List[Tag] = List.empty,
  metadataCount: Long = 0,
  @deprecated("use Metadata","since the use of jsonld") jsonldMetadata: List[Metadata] = List.empty,
  @deprecated("use Metadata","since the use of jsonld") metadata: Map[String, Any] = Map.empty,
  @deprecated("use Metadata","since the use of jsonld") userMetadata: Map[String, Any] = Map.empty,
  collections: List[UUID] = List.empty,
  thumbnail_id: Option[String] = None,
  @deprecated("use Metadata","since the use of jsonld") datasetXmlMetadata: List[DatasetXMLMetadata] = List.empty,
  @deprecated("use Metadata","since the use of jsonld") userMetadataWasModified: Option[Boolean] = None,
  licenseData: LicenseData = new LicenseData(),
  spaces: List[UUID] = List.empty,
  lastModifiedDate: Date = new Date(),
  followers: List[UUID] = List.empty,
  status: String = DatasetStatus.PRIVATE.toString// dataset has four status: trial, default, private and public. yet editors of the dataset
  // can only see the default, private and public, where trial equals to private. viewers can only see private and
  // public, where trial and default equals to private/public of its space
){
  def isPublic:Boolean = status.contains(DatasetStatus.PUBLIC.toString)
  def isDefault:Boolean = status.contains(DatasetStatus.DEFAULT.toString)
  def isTRIAL:Boolean = status.contains(DatasetStatus.TRIAL.toString)
  def inSpace:Boolean = spaces.size > 0
}

object DatasetStatus extends Enumeration {
  type DatasetStatus = Value
  val PUBLIC, PRIVATE, DEFAULT, TRIAL = Value
}


object Dataset {
  implicit val datasetWrites = new Writes[Dataset] {
    def writes(dataset: Dataset): JsValue = {
      val datasetThumbnail = if(dataset.thumbnail_id.isEmpty) {
        "None"
      } else {
        dataset.thumbnail_id.toString().substring(5,dataset.thumbnail_id.toString().length-1)
      }
      Json.obj("id" -> dataset.id.toString, "name" -> dataset.name, "description" -> dataset.description,
        "created" -> dataset.created.toString, "thumbnail" -> datasetThumbnail, "authorId" -> dataset.author.id, "spaces" -> dataset.spaces)
    }
  }
}