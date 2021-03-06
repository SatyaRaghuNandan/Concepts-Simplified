import org.apache.spark.ml.feature.{OneHotEncoder, StringIndexer}
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.functions._
import org.apache.spark.ml.clustering.{KMeans, KMeansModel}
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.ml.Pipeline

val df = sqlContext.createDataFrame(Seq(
  (0, "a", "Desktop", 2),
  (1, "b", "Mobile", 2),
  (2, "c", "", 3),
  (3, "", "appleTV", 3),
  (4, "a", "Desktop", 2),
  (5, "c", "Mobile", 2)
)).toDF("user_id", "category", "device", "channel_id")
df.show()

val temp = df.na.replace(Seq("category", "device"), Map("" -> "NA"))
temp.show()

val categorical_features = Array("category", "device")
val indexer: Array[org.apache.spark.ml.PipelineStage] = categorical_features.map(
  fName => new StringIndexer()
    .setInputCol(fName)
    .setOutputCol(s"${fName}Index"))
val index_pipeline = new Pipeline().setStages(indexer)
val index_model = index_pipeline.fit(temp)
val dfIndexed = index_model.transform(temp)


val indexColumns  = dfIndexed.columns.filter(x => x contains "Index")
val encoder:Array[org.apache.spark.ml.PipelineStage] = indexColumns.map(
  fName => new OneHotEncoder()
    .setInputCol(fName)
    .setOutputCol(s"${fName}Vec"))

val encoderPipeline = new Pipeline().setStages(encoder)
val hotEncodedDF = encoderPipeline.fit(dfIndexed).transform(dfIndexed)

val assembler = new VectorAssembler().setInputCols(Array("categoryIndexVec", "deviceIndexVec", "channel_id")).setOutputCol("features")
val model = new KMeans().setSeed(0).setK(5).setMaxIter(10).setFeaturesCol("features").setPredictionCol("prediction")
val pipeline = new Pipeline().setStages(Array(assembler, model))
val kMeansModelFit = pipeline.fit(hotEncodedDF)

val transformedFeaturesDF = kMeansModelFit.transform(hotEncodedDF)
transformedFeaturesDF.show()
val cost = kMeansModelFit.stages(1).asInstanceOf[KMeansModel].computeCost(transformedFeaturesDF)
println(cost)
println("Cluster Centers: ")
kMeansModelFit.stages(1).asInstanceOf[KMeansModel].clusterCenters.foreach(println)