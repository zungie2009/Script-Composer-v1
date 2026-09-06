package org.roberttu.llmscriptcomposer;
import java.util.*;

/**
 * In-memory catalog of public application and user-interface questionnaire presets.
 * Presets declare explicit questions, options, validation limits, and output targets.
 */
public final class PresetCatalog {
  private final PresetIntrospector introspector = new PresetIntrospector();
  private final List<Map<String, Object>> presets;

  /** Builds and validates the complete 100-preset catalog. */
  public PresetCatalog() {
    List<Map<String, Object>> all = new ArrayList<>();
    for (String row : DOMAINS) {
      all.add(domain(row));
    }
    for (String row : UI) {
      all.add(ui(row));
    }
    if (all.size() != 100) {
      throw new IllegalStateException("Expected 100 presets, found " + all.size());
    }
    presets = List.copyOf(all);
  }
  /** @return the immutable catalog in display order */
  public List<Map<String, Object>> all() {
    return presets;
  }

  /**
   * Finds one preset.
   * @param id preset identifier
   * @return matching preset
   */
  public Map<String, Object> get(String id) {
    return presets.stream()
        .filter(preset -> id.equals(preset.get("id")))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown preset: " + id));
  }

  private Map<String, Object> domain(String row) {
    String[] columns = row.split("\\|", 3);
    List<String> entities = List.of(columns[2].split(","));
    return preset(columns[0], columns[1], "APPLICATION", entities, List.of(
      q("audience","Who are we building for?","SINGLE_SELECT",1,List.of("A business owner","An internal team","Customers or members","Multiple groups"),"application.audience"),
      q("goals","What are you trying to achieve?","MULTI_SELECT",1,List.of("Organize daily operations","Reduce manual work","Improve customer service","Track performance","Replace spreadsheets","Create a new digital service"),"requirements.goals"),
      q("users","Who will use the application?","MULTI_SELECT",1,List.of("Business owner or administrator","Managers","Employees or specialists","Customers or clients","Vendors or partners"),"requirements.users"),
      q("priorities","What matters most?","MULTI_SELECT",2,List.of("Simple to learn","Fast data entry","Accurate records","Workflow automation","Reporting and visibility","Mobile access","Security and auditability"),"requirements.priorities"),
      q("records","Which records must the application manage?","MULTI_SELECT",1,entities,"domain.entities"),
      q("workflows","Which operational patterns are required?","MULTI_SELECT",1,List.of("Create and maintain records","Search, filter and sort","Schedule people or resources","Assign and track work","Approve or reject requests","Manage inventory or availability","Generate reports","Send notifications"),"behavior.workflows"),
      q("platform","Which implementation should the LLM target?","SINGLE_SELECT",1,List.of("Plain Java web application","Java with a developer-selected framework",".NET web application","TypeScript web application","Python web application","Let the LLM recommend"),"delivery.platform"),
      q("persistence","How should application data be persisted?","SINGLE_SELECT",1,List.of("File-backed local storage","Relational database","Document database","In-memory demonstration only","Let the LLM recommend"),"delivery.persistence"),
      q("security","What access protection is required?","MULTI_SELECT",1,List.of("No login for the first prototype","Administrator login","Role-based access","Customer or member accounts","Audit history","Sensitive-data protection"),"delivery.security"),
      q("quality","What must be verified before delivery?","MULTI_SELECT",2,List.of("Application starts successfully","All declared workflows operate on real state","Invalid input is rejected safely","Persistence survives restart","Relationships preserve referential integrity","Major pages are bookmarkable","Automated tests pass"),"acceptance.criteria")));
  }
  private Map<String, Object> ui(String row) {
    String[] columns = row.split("\\|", 3);
    List<String> concepts = List.of(columns[2].split(","));
    return preset(columns[0], columns[1], "UI", concepts, List.of(
      q("audience","Who are we designing for?","SINGLE_SELECT",1,List.of("Business operators","Administrators","Customers or members","Field or mobile workers","General public"),"ui.audience"),
      q("goals","What should this interface help users accomplish?","MULTI_SELECT",1,List.of("Find information quickly","Create or edit records","Complete a guided task","Monitor current conditions","Make decisions","Communicate or collaborate"),"ui.goals"),
      q("components","Which interface elements are required?","MULTI_SELECT",1,concepts,"ui.components"),
      q("navigation","How should major areas be organized?","SINGLE_SELECT",1,List.of("Top navigation","Left sidebar","Dashboard with task cards","Guided step-by-step flow","Single focused workspace"),"ui.navigation"),
      q("devices","Which devices matter?","MULTI_SELECT",1,List.of("Desktop","Laptop","Tablet","Mobile phone","Kiosk or shared terminal"),"ui.devices"),
      q("states","Which interaction states must be designed?","MULTI_SELECT",2,List.of("Loading","Empty","Validation error","System error","Success confirmation","No permission","Offline or disconnected"),"ui.states"),
      q("accessibility","What accessibility level is required?","SINGLE_SELECT",1,List.of("WCAG 2.2 AA","WCAG 2.2 AAA","Best practical accessibility"),"ui.accessibility"),
      q("framework","Which implementation should the LLM target?","SINGLE_SELECT",1,List.of("HTML, CSS and vanilla JavaScript","React","Angular","Vue","Let the LLM recommend"),"delivery.framework"),
      q("quality","What must be verified before delivery?","MULTI_SELECT",2,List.of("Responsive behavior","Keyboard navigation","Readable contrast","Clear validation feedback","Stable bookmarkable routes","No dead controls","Realistic empty and error states"),"acceptance.criteria")));
  }
  private Map<String, Object> preset(
      String id,
      String name,
      String type,
      List<String> concepts,
      List<Map<String, Object>> questions) {
    Map<String, Object> preset = new LinkedHashMap<>();
    preset.put("id", id);
    preset.put("name", name);
    preset.put("type", type);
    preset.put("concepts", concepts);
    preset.put("questions", questions);
    preset.put("introspection", introspector.describe(id, name, type, concepts));
    return preset;
  }

  private Map<String, Object> q(
      String id,
      String title,
      String selectionType,
      int minimumSelections,
      List<String> options,
      String target) {
    return Map.of(
        "id", id,
        "title", title,
        "selectionType", selectionType,
        "minimumSelections", minimumSelections,
        "options", options,
        "target", target);
  }

  private static final String[] DOMAINS={
    "auto-repair|Auto Repair Shop|Customer,Vehicle,Work Order,Technician,Appointment,Part,Estimate,Invoice",
    "car-wash|Car Wash|Customer,Vehicle,Wash Package,Wash Job,Service Bay,Supply",
    "restaurant|Restaurant|Customer,Reservation,Table,Menu Item,Order,Ingredient,Employee",
    "cafe|Cafe and Coffee Shop|Customer,Menu Item,Order,Barista,Inventory Item,Supplier",
    "bakery|Bakery|Customer,Product,Recipe,Ingredient,Production Batch,Order,Employee",
    "hotel|Hotel|Guest,Room,Reservation,Stay,Housekeeping Task,Employee,Invoice",
    "motel|Motel|Guest,Room,Reservation,Housekeeping Task,Maintenance Request,Payment",
    "bed-breakfast|Bed and Breakfast|Guest,Room,Reservation,Breakfast Plan,Housekeeping Task",
    "retail|Retail Store|Customer,Product,Inventory Item,Sale,Employee,Supplier",
    "grocery|Grocery Store|Customer,Product,Perishable Lot,Sale,Employee,Supplier",
    "hardware|Hardware Store|Customer,Product,Inventory Item,Sale,Supplier,Reorder",
    "clothing|Clothing Boutique|Customer,Product,Variant,Inventory Item,Sale,Supplier",
    "jewelry|Jewelry Store|Customer,Item,Repair,Appraisal,Sale,Employee",
    "furniture|Furniture Store|Customer,Product,Inventory Item,Sale,Delivery,Supplier",
    "pharmacy|Pharmacy Operations|Customer,Product,Inventory Lot,Prescription,Sale,Employee",
    "wholesale|Wholesale Distributor|Customer,Product,Inventory Item,Order,Purchase Order,Shipment",
    "marketplace|Classified Marketplace|Seller,Buyer,Listing,Category,Inquiry,Order",
    "property-management|Property Management|Property,Tenant,Lease,Maintenance Request,Vendor,Payment",
    "commercial-property|Commercial Property Management|Property,Tenant,Lease,Work Order,Vendor,Inspection",
    "vacation-rental|Vacation Rental Management|Property,Guest,Reservation,Cleaning Task,Maintenance Request,Payment",
    "hoa|HOA Management|Community,Owner,Property,Dues,Request,Vendor,Violation",
    "self-storage|Self Storage Facility|Unit,Tenant,Rental,Payment,Maintenance Request",
    "coworking|Coworking Space|Member,Plan,Desk,Room,Reservation,Facility Issue",
    "general-contractor|General Contractor|Customer,Project,Crew,Schedule,Material,Cost,Subcontractor",
    "remodeling|Remodeling Company|Customer,Project,Estimate,Subcontractor,Material,Budget,Schedule",
    "roofing|Roofing Company|Lead,Customer,Estimate,Project,Crew,Material,Schedule",
    "painting|Painting Contractor|Customer,Estimate,Project,Crew,Supply,Schedule",
    "carpentry|Carpentry Business|Customer,Project,Craftsperson,Material,Estimate,Schedule",
    "electrical|Electrical Contractor|Customer,Job,Electrician,Schedule,Material,Estimate",
    "flooring|Flooring Contractor|Customer,Measurement,Estimate,Installer,Material,Job",
    "solar|Solar Installer|Lead,Customer,Site Survey,Project,Crew,Equipment,Inspection",
    "landscaping|Landscaping Company|Customer,Property,Job,Crew,Schedule,Equipment,Supply",
    "lawn-care|Lawn Care Service|Customer,Property,Service Plan,Crew,Route,Visit,Supply",
    "cleaning|Cleaning Service|Customer,Site,Service Plan,Crew,Job,Schedule,Supply",
    "janitorial|Janitorial Operations|Client Site,Crew,Recurring Task,Supply,Inspection,Schedule",
    "pest-control|Pest Control Company|Customer,Property,Service Plan,Technician,Visit,Material",
    "pool-service|Pool Service Company|Customer,Pool,Route,Technician,Visit,Chemical",
    "handyman|Handyman Service|Customer,Job,Estimate,Technician,Schedule,Material,Task",
    "appliance-repair|Appliance Repair|Customer,Appliance,Service Ticket,Technician,Appointment,Part",
    "electronics-repair|Electronics Repair Shop|Customer,Device,Repair Ticket,Technician,Part,Payment",
    "auto-body|Auto Body Shop|Customer,Vehicle,Estimate,Repair Job,Technician,Part,Insurance Claim",
    "auto-detailing|Auto Detailing|Customer,Vehicle,Service Package,Appointment,Detailer,Supply",
    "towing|Towing Company|Customer,Service Call,Dispatcher,Driver,Truck,Location,Invoice",
    "fleet-management|Fleet Management|Vehicle,Driver,Maintenance Event,Assignment,Fuel Record,Cost",
    "vehicle-rental|Vehicle Rental|Customer,Vehicle,Reservation,Rental,Maintenance Event,Payment",
    "trucking|Trucking Company|Customer,Load,Driver,Truck,Route,Delivery,Maintenance Event",
    "logistics|Logistics Company|Customer,Shipment,Driver,Vehicle,Route,Delivery Event",
    "courier|Courier Service|Customer,Shipment,Courier,Route,Delivery Event,Payment",
    "moving|Moving Company|Customer,Estimate,Move,Crew,Truck,Schedule,Inventory Item",
    "health-clinic|Health Clinic|Patient,Practitioner,Appointment,Encounter,Treatment Plan,Invoice",
    "chiropractic|Chiropractic Practice|Patient,Practitioner,Appointment,Treatment Plan,Visit,Invoice",
    "physical-therapy|Physical Therapy Practice|Patient,Therapist,Appointment,Treatment Plan,Progress Note",
    "optometry|Optometry Practice|Patient,Exam,Appointment,Prescription,Eyewear Order,Inventory Item",
    "home-care|Home Care Agency|Client,Caregiver,Visit,Schedule,Care Task,Care Plan",
    "senior-living|Senior Living Facility|Resident,Room,Employee,Care Task,Activity,Incident",
    "wellness|Wellness Counseling Practice|Client,Practitioner,Appointment,Service Plan,Session Note",
    "hair-salon|Hair Salon|Customer,Stylist,Service,Appointment,Product,Sale",
    "barbershop|Barbershop|Customer,Barber,Service,Appointment,Product,Sale",
    "spa|Spa|Customer,Practitioner,Treatment,Appointment,Room,Supply",
    "pet-grooming|Pet Grooming|Customer,Pet,Groomer,Service,Appointment,Supply",
    "gym|Gym and Fitness Center|Member,Membership,Class,Trainer,Enrollment,Equipment",
    "daycare|Daycare Center|Child,Guardian,Employee,Attendance,Schedule,Supply,Incident",
    "private-school|Private School|Student,Guardian,Teacher,Class,Enrollment,Schedule",
    "driving-school|Driving School|Student,Instructor,Lesson,Vehicle,Schedule,Payment",
    "tutoring|Tutoring Business|Student,Tutor,Subject,Lesson,Schedule,Progress Note",
    "dance-studio|Dance Studio|Student,Instructor,Class,Room,Enrollment,Performance",
    "music-school|Music School|Student,Instructor,Lesson,Room,Schedule,Recital",
    "training|Professional Training Company|Learner,Instructor,Course,Session,Enrollment,Completion",
    "consulting|Consulting Firm|Client,Engagement,Consultant,Deliverable,Time Entry,Invoice",
    "law-firm|Law Firm|Client,Matter,Attorney,Document,Deadline,Time Entry,Invoice",
    "bookkeeping|Bookkeeping Service|Client,Recurring Work,Document,Deadline,Employee,Time Entry",
    "insurance|Insurance Agency|Customer,Policy,Agent,Claim,Renewal,Payment",
    "translation|Translation Agency|Client,Project,Translator,Language,File,Deadline",
    "photography|Photography Business|Client,Booking,Shoot,Editing Task,Deliverable,Invoice",
    "event-planning|Event Planning Company|Client,Event,Vendor,Budget,Schedule,Task",
    "catering|Catering Company|Client,Event,Menu,Employee,Ingredient,Purchase,Delivery",
    "meal-prep|Meal Prep Business|Customer,Subscription,Menu,Ingredient,Production Batch,Delivery",
    "food-truck|Food Truck|Location,Employee,Menu Item,Inventory Item,Order,Schedule",
    "bar-lounge|Bar and Lounge|Customer,Employee,Inventory Item,Menu Item,Event,Order",
    "nonprofit|Nonprofit Organization|Donor,Volunteer,Program,Event,Donation,Outreach Task"
  };
  private static final String[] UI={
    "ui-admin-dashboard|Administration Dashboard|Metric Card,Navigation,Activity Feed,Alert,Quick Action",
    "ui-crud|CRUD Management Interface|Data Table,Form,Search,Filter,Pagination,Validation",
    "ui-master-detail|Master Detail Workspace|Master List,Detail Panel,Editor,Related Records",
    "ui-search-table|Searchable Table Editor|Search,Filter,Sortable Table,Inline Action,Editor",
    "ui-multistep|Multi-step Form|Progress Indicator,Step Form,Validation,Review,Submission",
    "ui-calendar|Scheduling Calendar|Calendar,Resource Filter,Event Editor,Availability,Conflict Alert",
    "ui-kanban|Kanban Board|Board,Column,Card,Drag Drop,Filter,Detail Drawer",
    "ui-reporting|Reporting Dashboard|Metric,Chart,Filter,Date Range,Export,Drilldown",
    "ui-customer-portal|Customer Portal|Dashboard,Request Form,History,Document,Profile",
    "ui-mobile-field|Mobile Field Interface|Job List,Job Detail,Checklist,Photo,Signature,Offline State",
    "ui-inventory|Inventory Workspace|Stock Table,Item Editor,Adjustment,Reorder Alert,Supplier",
    "ui-pos|Point of Sale Interface|Product Search,Cart,Discount,Payment,Receipt",
    "ui-booking|Appointment Booking Interface|Service Selection,Provider,Availability,Customer Form,Confirmation",
    "ui-documents|Document Management Interface|Folder,Document List,Preview,Upload,Metadata,Search",
    "ui-settings|Settings Console|Category Navigation,Form,Validation,Save State,Audit History",
    "ui-landing|Responsive Landing Page|Hero,Feature,Testimonial,Pricing,Call To Action,Footer",
    "ui-visualization|Data Visualization Dashboard|Chart,Metric,Filter,Legend,Drilldown,Export",
    "ui-approval|Approval Workflow Interface|Queue,Request Detail,Comment,Approve,Reject,History",
    "ui-authentication|Authentication Screens|Sign In,Registration,Password Reset,MFA,Session Error",
    "ui-design-system|Design System Starter|Token,Typography,Color,Component,Pattern,Documentation"
  };
}
