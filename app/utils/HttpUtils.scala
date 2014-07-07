package utils

import models.classified.{Price, Advertisement}
import models.classified.criteria.transport.cars._
import models.tasks.Task
import org.jsoup._
import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer
import models._
import org.jsoup.nodes.Document
import play.api.cache.Cache
import play.api.Play.current

object HttpUtils {
  val url = "http://www.ss.lv"

  def tasks: List[Task] = {
    val saab93Petrol = new Task("saab/9-3", "SAAB 9-3 Petrol")
    saab93Petrol.fuel = new Petrol
    saab93Petrol.cabin = new Estate
    saab93Petrol.gearbox = new Manual
    saab93Petrol.rails = true

    val saab93Diesel = new Task("saab/9-3", "SAAB 9-3 Diesel")
    saab93Diesel.priceMax = Some(5700)
    saab93Diesel.fuel = new Diesel
    saab93Diesel.cabin = new Estate
    saab93Diesel.gearbox = new Manual
    saab93Diesel.rails = true

    val saab95Petrol = new Task("saab/9-5", "SAAB 9-5 Petrol")
    saab95Petrol.fuel = new Petrol
    saab95Petrol.cabin = new Estate
    saab95Petrol.gearbox = new Manual
    saab95Petrol.mileageMax = Some(220000)
    saab95Petrol.rails = true

    val saab95LPG = new Task("saab/9-5", "SAAB 9-5 LPG")
    saab95LPG.fuel = new PetrolGas
    saab95LPG.cabin = new Estate
    saab95LPG.gearbox = new Manual
    saab95LPG.mileageMax = Some(220000)
    saab95LPG.rails = true

    val volvoV40Petrol = new Task("volvo/v40", "Volvo V40 Petrol")
    volvoV40Petrol.priceMin = Some(2000)
    volvoV40Petrol.mileageMax = Some(200000)
    volvoV40Petrol.fuel = new Petrol
    volvoV40Petrol.gearbox = new Manual
    volvoV40Petrol.rails = true

    val volvoV50 = new Task("volvo/v50", "Volvo V50")
    volvoV50.mileageMax = Some(200000)
    volvoV50.gearbox = new Manual
    volvoV50.rails = true
    volvoV50.priceMax = Some(5700)

    List(
      saab93Petrol,
      saab93Diesel,
      saab95Petrol,
      saab95LPG,
      volvoV40Petrol,
      volvoV50
    )
  }

  def parseTasks: List[(Task, List[Advertisement])] = {
    for (t <- tasks) yield (t -> fetchAdverts(t))
  }

  def mileage(text: String): Option[Int] = {
    if (text.contains(" ")) {
      var mileageParts = text.split(" ")
      val mileage = mileageParts(0).toInt
      Some(mileage)
    }
    else {
      None
    }
  }

  def price(text: String): Option[Price] = {
    if (text.contains(" ")) {
      var priceParts = text.split(" ")

      val price = priceParts(0).replace(",", "").toInt
      val currency = priceParts(1)
      Some(Price(price, currency))
    }
    else {
      None
    }
  }

  def fetchAdverts(task: Task): List[Advertisement] = {
    val connection = Jsoup.connect(url + task.url);

    // handle fuel type
    val fuelTypeId = task.fuel match {
      case _: Petrol => "493"
      case _: PetrolGas => "495"
      case _: Diesel => "494"
      case _ => null
    }
    if (fuelTypeId != null) connection.data("opt[34][]", fuelTypeId)

    // handle cabin type
    val cabinTypeId = task.cabin match {
      case _: Estate => "483"
      case _: Sedan => "484"
      case _ => null
    }
    if (cabinTypeId != null) connection.data("opt[32][]", cabinTypeId)

    // handle gearbox type
    val gearboxTypeId = task.gearbox match {
      case _: Automatic => "497"
      case _: Manual => "496"
      case _ => null
    }
    if (gearboxTypeId != null) connection.data("opt[35][]", gearboxTypeId)

    if (!task.priceMin.isEmpty) connection.data("topt[8][min]", task.priceMin.get.toString)
    if (!task.priceMax.isEmpty) connection.data("topt[8][max]", task.priceMax.get.toString)

    if (!task.mileageMin.isEmpty) connection.data("topt[16][min]", task.mileageMin.get.toString)
    if (!task.mileageMax.isEmpty) connection.data("topt[16][max]", task.mileageMax.get.toString)

    // to sell only
    connection.data("sid", "1")

    if (task.rails) {
      connection.data("checkbox[961]", "1")
      connection.data("opt[961][]", "17311")
    }

    val key: String = url + task.url + connection.request.data.toString
    val html: String = Cache.getOrElse[String](key) {
      null
    }
    var doc: Document = null

    if (html != null) {
      doc = Jsoup.parse(html)
    }
    else {
      doc = connection.post
      Cache.set(key, doc.html())
    }

    val descriptions = doc.select("div.d1 a").map(_.text())
    val urls = doc.select("div.d1 a").map(_.attr("href"))
    val years = doc.select("tr[height=44] td:eq(3)").map(_.text())
    val engines = doc.select("tr[height=44] td:eq(4)").map(_.text())
    val mileages = doc.select("tr[height=44] td:eq(5)").map(_.text())
    val prices = doc.select("tr[height=44] td:eq(6)").map(_.text())
    val imageUrls = doc.select("td.msga2 img").map(_.attr("src"))

    val adverts = new ListBuffer[Advertisement]
    for (i <- 0 until descriptions.size) {
      val year = years(i)
      if (year != "-") {

        val ad = Advertisement(
          description = descriptions(i),
          url = urls(i),
          year = years(i).toInt,
          engineSize = engines(i),
          imageUrl = imageUrls(i),
          mileage = mileage(mileages(i)),
          price = price(prices(i))
        )
        adverts += ad
      }
    }
    adverts.toList.sortBy(a => a.price.map(_.price))
  }
}
