# Pharmacy System — نظام إدارة الصيدلية

A bilingual (Arabic / English, **Arabic by default**) pharmacy management system, built from the Store System:
point of sale with barcode scanner, medicines with **batches and expiry dates**, sale by **box / strip / unit**,
purchases with **bonus** and supplier discounts, returns, customer & supplier accounts, expiry tracking,
reports (PDF / Excel) and invoice printing (80mm thermal + A4).

**Stack:** Java 17+ · Spring Boot 3.3 · Spring Security · Thymeleaf · Bootstrap 5 (RTL) · MySQL / MariaDB (XAMPP) · ZXing · OpenPDF · Apache POI

## Pharmacy features (differences from the Store System)

| Area | How it works |
|---|---|
| **Medicine record** | Trade name (AR/EN), scientific name (active ingredient), strength, dosage form, manufacturer, shelf location, fridge / prescription / VAT flags |
| **Pack structure** | 1 box = N strips × M units. Prices (public price, cost) and reorder level are **per box**; stock is kept in the **smallest unit** (whole numbers) and shown as "3 علبة + 1 شريط + 4 قرص" |
| **Batches & expiry** | Every stock change is per batch (batch no. + expiry). Product stock always equals the sum of its batches |
| **FEFO** | Sales take stock from the batch that expires first. **Expired stock can never be sold** |
| **Returns** | Sales returns go back into the exact batches they came from; purchase returns come out of that purchase line's batch |
| **POS** | Search by trade / scientific name or barcode, sell by box / strip / unit, Rx / fridge / near-expiry warnings, **substitutes** (same active ingredient, in stock) |
| **Purchases** | Batch no. and expiry per line (required for medicines), **bonus boxes (بونص)**, supplier discount % per line; bonus lowers the effective cost per box |
| **VAT per item** | Medicines exempt, cosmetics taxable (flag on each item); invoices and the VAT report split taxable / exempt |
| **Expiry** | Expiry screen (expired / 30 / 60 / 90 / 180 days), write-off (إتلاف) of expired batches, dashboard alerts |
| **Reports** | Expired medicines, near-expiry (90 days), reorder suggestions, dead stock, plus all Store System reports |
| **Stock adjustment** | Per batch (or a new batch), in box / strip / unit, add/remove or set counted quantity |

Everything else (users & roles, customers/suppliers & statements, return settlement, printing, PDF with bundled Arabic font,
Excel, settings, discounts in amount or %, cost method, backup, cPanel deployment) is the same as the Store System.

---

## 1. Run it

1. Start **MySQL** from the XAMPP control panel.
2. From the project folder:

```bash
mvn spring-boot:run
```

3. Open <http://localhost:8080> and log in with **admin / admin123**. Change this password from **Users**.

The database `pharmacy_system` is created automatically on the first run (tables are created and updated by Hibernate).
Sample products, a demo customer and a demo supplier are added the first time, when the products table is empty.
To turn this off, set `app.seed-demo-data: false`.

To build a single runnable jar:

```bash
mvn clean package
```

```bash
java -jar target/pharmacy-system.war
```

A production (cPanel) release package has not been built yet for the pharmacy system; it follows the same steps as the Store System (see the Store System `release/.../DEPLOY.md`), using `~/pharmacy-system/config.yml`.

## 2. Configuration

`src/main/resources/application.yml`

| Setting | Default | Notes |
|---|---|---|
| `spring.datasource.url` | `jdbc:mariadb://localhost:3306/pharmacy_system` | XAMPP ships MariaDB. For MySQL 8, use the `jdbc:mysql://` URL in the comment |
| `spring.datasource.username/password` | `root` / empty | XAMPP default |
| `app.currency` | `EGP` | initial value only |
| `app.tax.enabled` / `app.tax.rate` | `true` / `14` | initial value only |
| `app.default-language` | `ar` | initial value only |
| `app.upload-dir` | `./uploads` | logo and product images |
| `app.backup.mysqldump-path` | `C:/xampp/mysql/bin/mysqldump.exe` | used by *Settings → Download backup* |
| `app.pdf.fonts` | Cairo → Tahoma → Arial | first existing font is used for Arabic text in PDFs |

After the first run, everything below is edited from the **Settings** page (admin only) and stored in the database:

* **Store details:** name (AR/EN), address (AR/EN), mobile 1, mobile 2, landline, email, tax number, commercial register, **logo**, invoice footer (AR/EN)
* **Configuration:** currency, **VAT on/off** and rate, default language, allow selling without stock

## 3. Pages

| Menu | What it does |
|---|---|
| Dashboard | Today / month sales, today's profit, low stock, receivables/payables, 30-day chart, top products |
| **Point of Sale** | Scan a barcode (or type a name) → cart → cash / card / credit → prints the receipt. Keys: **F2** search, **F9** pay |
| Sales invoices | Search by number / customer / date; view, print 80mm or A4, PDF, return items |
| Returns | Sales returns (scan the receipt barcode to find the invoice) and purchase returns |
| Customers / Suppliers | Accounts with opening balance, **account statement**, receive or make payments |
| New purchase / Purchases | Scan items in, set cost and sale price; stock increases and the average cost updates |
| Products | Bilingual names, category, unit, cost/sale price, min stock, image, **automatic EAN-13 barcode** |
| Barcode labels | Choose products and copies; 38×25, 50×30, 58×40 mm roll labels or an A4 sheet (3×8) |
| Categories / Units | Bilingual lookup lists |
| Stock adjustment | Add or remove quantity (damaged, gifts…) or set the counted quantity (stock count) |
| Stock movements | Full history of every stock change, with a running balance |
| Reports | Daily sales, by product / category / cashier, purchases by supplier / product, **profit & loss**, best / slow sellers, low stock, inventory valuation, customer / supplier balances, VAT summary. Each can be **printed** or exported to **PDF / Excel** |
| Users | Roles: **Admin** (everything), **Manager** (everything except users and settings), **Cashier** (POS, sales, sales returns, customers) |
| Settings | Store details, logo, VAT, currency, language, backup |

## 4. Barcodes

* When a product is created without a barcode, the system generates a valid **EAN-13** in the in-store range (`200…`).
* A barcode already printed on the item can be scanned into the barcode field instead.
* USB and Bluetooth scanners work in "keyboard" mode: scan into the POS or purchase search box and the item is added.
* Every invoice prints its number as a Code-128 barcode. Scan it on the **Returns** page to open the invoice.

## 5. Business rules

* Amounts: `line total = qty × price − line discount`; `VAT = (subtotal − invoice discount) × rate`.
* A credit or partially paid sale requires a customer. The unpaid part is added to the customer balance.
* Purchases update the product cost with a **weighted average**, and can also update the sale price.
* Returns refund each item's share of the invoice discount plus VAT. The amount is either refunded in cash or deducted from the account balance.
* Selling more than the available stock is blocked, unless *allow selling without stock* is enabled.
* Stock changes only through purchases, sales, returns and adjustments, and each change is logged.
* A product used in invoices cannot be deleted; it is deactivated instead.

## 6. Project layout

```
src/main/java/com/store
  config/      security, i18n (cookie locale, Arabic default), app properties, first-run seed data
  entity/      JPA entities
  repository/  Spring Data repositories
  service/     business logic: sales, purchases, returns, stock, barcodes, PDF, Excel, reports, backup
  controller/  MVC pages + /api JSON endpoints used by the POS
src/main/resources
  templates/   Thymeleaf pages (layout.html + fragments.html shared)
  static/      app.css, app.js (shared helpers), pos.js
  messages.properties (English) / messages_ar.properties (Arabic)
```

## 7. Troubleshooting

* **Arabic letters are disconnected in PDFs:** make sure one of the fonts in `app.pdf.fonts` exists. For example, put `Cairo-Regular.ttf` in a `fonts` folder next to the jar.
* **Backup fails:** check `app.backup.mysqldump-path`.
* **Port 8080 is busy:** change `server.port`.
