package api

import javax.inject.{Inject, Singleton}


import models.UUID
import services._
import play.api.libs.json._
import com.wordnik.swagger.annotations.{ApiResponse, ApiResponses, Api, ApiOperation}
/**
 * Created by indiragp on 8/31/15.
 */
@Api(value="/curations", listingPath= "/api-docs.json/curations", description = "A curation object is a dataset ready for publication")
@Singleton
class CurationObjects @Inject()(
                                 datasets: DatasetService,
                               curations: CurationService,
                               files: FileService,
                               comments: CommentService,
                               sections: SectionService
                                 ) extends ApiController{
  @ApiOperation(value=" Get Curation object ORE map",
  httpMethod="GET")
  def getCurationObjectOre(curationId: UUID) = PrivateServerAction {
    implicit request =>
      curations.get(curationId) match {
        case Some(c) => {
          //          var filesJson =""
          val filesJson = c.datasets(0).files.map { file =>
            Json.toJson(Map(
              "Identifier" -> Json.toJson(file.id),
              "Creation Date" -> Json.toJson(file.uploadDate),
              "Label" -> Json.toJson(""),
              "Title" -> Json.toJson(file.filename),
              "Uploaded By" -> Json.toJson(file.author.avatarUrl.getOrElse("")),
              "Abstract" -> Json.toJson(""),
              "Creator" -> Json.toJson(Seq(file.author.fullName, ": " , file.author.avatarUrl.getOrElse(""))),
              "Publication Date" -> Json.toJson(""),
              "External Identifier" -> Json.toJson(""),
              "Keyword" -> Json.toJson(file.tags.map(_.name)),
              "@type" -> Json.toJson(Seq("AggregatedResource", "")),
              "Version Of" -> Json.toJson(""),
              "Has Part" ->Json.toJson("")
            ))
          }

          val filesInDataset = c.datasets(0).files.map(f => files.get(f.id).get) //.sortBy(_.uploadDate)
          var commentsByDataset = comments.findCommentsByDatasetId(c.datasets(0).id)
          filesInDataset.map {
            file =>

              commentsByDataset ++= comments.findCommentsByFileId(file.id)
              sections.findByFileId(UUID(file.id.toString)).map { section =>
                commentsByDataset ++= comments.findCommentsBySectionId(section.id)
              }
          }
          commentsByDataset = commentsByDataset.sortBy(_.posted)
          val commentsJson =  commentsByDataset.map { comm =>
            Json.toJson(Map(
              "comment_body" -> Json.toJson(comm.text),
              "comment_date" -> Json.toJson(comm.posted),
              "Identifier" -> Json.toJson(comm.id),
              "comment_author" -> Json.toJson(comm.author.fullName)
            ))
          }


          val parsedValue= Json.toJson(
            Map(
              "@context" -> Json.toJson(Seq(
                Json.toJson("https://w3id.org/ore/context"),
                Json.toJson(
                  Map(
                    "Identifier"-> Json.toJson("http://purl.org/dc/elements/1.1/identifier"),
                    "License"-> Json.toJson("http://purl.org/dc/terms/license"),
                    "Rights Holder"-> Json.toJson("http://purl.org/dc/terms/rightsHolder"),
                    "Rights"-> Json.toJson("http://purl.org/dc/terms/rights"),
                    "Date"-> Json.toJson("http://purl.org/dc/elements/1.1/date"),
                    "Creation Date"-> Json.toJson("http://purl.org/dc/terms/created"),
                    "Size"-> Json.toJson("tag:tupeloproject.org,2006:/2.0/files/length"),
                    "Label"-> Json.toJson("http://www.w3.org/2000/01/rdf-schema#label"),
                    "Mimetype"-> Json.toJson("http://purl.org/dc/elements/1.1/format"),
                    "Description"-> Json.toJson("http://purl.org/dc/elements/1.1/description"),
                    "Title"-> Json.toJson("http://purl.org/dc/elements/1.1/title"),
                    "Uploaded By"-> Json.toJson("http://purl.org/dc/elements/1.1/creator"),
                    "Abstract"-> Json.toJson("http://purl.org/dc/terms/abstract"),
                    "Contact"-> Json.toJson("http://sead-data.net/terms/contact"),
                    "Creator"-> Json.toJson("http://purl.org/dc/terms/creator"),
                    "Publication Date"-> Json.toJson("http://purl.org/dc/terms/issued"),
                    "GeoPoint"-> Json.toJson(Seq(
                      Json.toJson(
                        Map(

                          "@id"-> Json.toJson("tag:tupeloproject.org,2006:/2.0/gis/hasGeoPoint"),
                          "long"-> Json.toJson("http://www.w3.org/2003/01/geo/wgs84_pos#long"),
                          "lat"-> Json.toJson("http://www.w3.org/2003/01/geo/wgs84_pos#lat")
                        )
                      )
                    )),
                    "Comment"->  Json.toJson( Seq( Json.toJson( Map(

                      "comment_body"-> Json.toJson("http://purl.org/dc/elements/1.1/description"),
                      "comment_date"-> Json.toJson("http://purl.org/dc/elements/1.1/date"),
                      "@id"-> Json.toJson("http://cet.ncsa.uiuc.edu/2007/annotation/hasAnnotation"),
                      "comment_author"-> Json.toJson("http://purl.org/dc/elements/1.1/creator")
                    )))),
                    "Where"-> Json.toJson("http:/sead-data.net/vocab/demo/where"),
                    "Has Description"-> Json.toJson("http://purl.org/dc/terms/description"),
                    "Experiment Site"-> Json.toJson("http://sead-data.net/terms/odm/location"),
                    "Experimental Method"-> Json.toJson("http://sead-data.net/terms/odm/method"),
                    "Primary Source"-> Json.toJson("http://www.w3.org/ns/prov#hadPrimarySource"),
                    "Topic"-> Json.toJson("http://purl.org/dc/terms/subject"),
                    "Audience"-> Json.toJson("http://purl.org/dc/terms/audience"),
                    "Bibliographic citation"-> Json.toJson("http://purl.org/dc/terms/bibliographicCitation"),
                    "http://purl.org/vocab/frbr/core#embodimentOf"-> Json.toJson("http://purl.org/vocab/frbr/core#embodimentOf"),
                    "Coverage"-> Json.toJson("http://purl.org/dc/terms/coverage"),
                    "Published In"-> Json.toJson("http://purl.org/dc/terms/isPartOf"),
                    "Publisher"-> Json.toJson("http://purl.org/dc/terms/publisher"),
                    "Quality Control Level"-> Json.toJson("http://sead-data.net/terms/odm/QualityControlLevel"),
                    "Who"-> Json.toJson("http://sead-data.net/vocab/demo/creator"),
                    "Alternative title"-> Json.toJson("http://purl.org/dc/terms/alternative"),
                    "External Identifier"-> Json.toJson("http://purl.org/dc/terms/identifier"),
                    "Proposed for Publication "-> Json.toJson("http://sead-data.net/terms/ProposedForPublication"),
                    "Start/End Date"-> Json.toJson("http://purl.org/dc/terms/temporal"),
                    "duplicates"-> Json.toJson("http://cet.ncsa.uiuc.edu/2007/mmdb/duplicates"),
                    "is derived from"-> Json.toJson("http://www.w3.org/ns/prov#wasDerivedFrom"),
                    "describes"-> Json.toJson("http://cet.ncsa.uiuc.edu/2007/mmdb/describes"),
                    "has newer version"-> Json.toJson("http://www.w3.org/ns/prov/#hadRevision"),
                    "relates to"-> Json.toJson("http://cet.ncsa.uiuc.edu/2007/mmdb/relatesTo"),
                    "references"-> Json.toJson("http://purl.org/dc/terms/references"),
                    "is referenced by"-> Json.toJson("http://purl.org/dc/terms/isReferencedBy"),
                    "has derivative"-> Json.toJson("http://www.w3.org/ns/prov/#hadDerivation"),
                    "has prior version"-> Json.toJson("http://www.w3.org/ns/prov#wasRevisionOf"),
                    "keyword"-> Json.toJson("http://www.holygoat.co.uk/owl/redwood/0.1/tags/taggedWithTag"),
                    "Is Version Of"-> Json.toJson("http://purl.org/dc/terms/isVersionOf"),
                    "Has Part"-> Json.toJson("http://purl.org/dc/terms/hasPart")
                  )
                )

              )),
              "Rights" -> Json.toJson(c.datasets(0).licenseData.m_licenseText),
              "describes" ->
                Json.toJson(Map(
                  "Identifier" -> Json.toJson(""),
                  "Creation Date" -> Json.toJson(c.created),
                  "Label" -> Json.toJson(""),
                  "Title" -> Json.toJson(c.name),
                  "Uploaded By" -> Json.toJson(c.author.fullName),
                  "Abstract" -> Json.toJson("This is an abstract"),
                  "Creator" -> Json.toJson(Seq(
                    Json.toJson(c.author.fullName + ": " + c.author.avatarUrl.getOrElse(""))
                  )),
                  "Publication Date" -> Json.toJson(c.created),
                  "Comment" -> Json.toJson(JsArray(commentsJson)),
                  "Published In" -> Json.toJson(""),
                  "Alternative title" -> Json.toJson(c.name),
                  "External Identifier" -> Json.toJson(""),
                  "Proposed for publication" -> Json.toJson("true"),
                  "keyword" -> Json.toJson(
                    Json.toJson(c.datasets(0).tags.map(_.name))
                  ),
                  "@id" -> Json.toJson(""),
                  "@type" -> Json.toJson(Seq("Aggregation", "http://cet.ncsa.uiuc.edu/2007/Collection")),
                  "Is Version of" -> Json.toJson(""),
                  "similarTo" -> Json.toJson(""),
                  "aggregates" -> Json.toJson(JsArray(filesJson)),
                  "Has Part" -> Json.toJson("")

                )),
              "Creation Date" -> Json.toJson(c.created),
              "Creator" -> Json.toJson(c.author.fullName),
              "@type" -> Json.toJson("ResourceMap"),
              "@id" -> Json.toJson(c.id)


            )

          )

          Ok(Json.toJson(parsedValue))
        }
        case None => InternalServerError("Curation Object not Found");
      }

  }
}
