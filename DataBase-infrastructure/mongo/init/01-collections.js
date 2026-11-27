// MongoDB creation script: the four collections with $jsonSchema validation, and their indexes.
// Runs automatically on the first start with an empty volume (docker-entrypoint-initdb.d).
// Safe to re-run on an existing database (creates missing collections, updates validators):
//   docker compose exec mongo mongosh usi /docker-entrypoint-initdb.d/01-collections.js
// Spec: DataBase-infrastructure/mongo/REQUIREMENTS.md
const usi = db.getSiblingDB("usi");

const str = { bsonType: "string" };
const strOrNull = { bsonType: ["string", "null"] };
const intT = { bsonType: ["int", "long"] };
const studyYear = { bsonType: ["int", "long"], minimum: 1, maximum: 6 };
const includeType = { enum: ["compulsory", "elective"] };
const role = { enum: ["director", "assistant"] };
const grade = { bsonType: ["double", "int", "null"], minimum: 1, maximum: 6 };
const ects = { bsonType: ["double", "int", "null"], minimum: 0, exclusiveMinimum: true };
const ref = (idField, extra) => ({
  bsonType: "array",
  items: { bsonType: "object", required: [idField], properties: Object.assign({ [idField]: str }, extra) },
});

const schemas = {
  programs: {
    bsonType: "object",
    required: ["_id", "name", "level"],
    properties: {
      _id: str, name: str, level: { enum: ["bachelor", "master"] },
      ects: intT, durationSemesters: intT, language: strOrNull, description: strOrNull,
      courses: ref("courseId", { title: str, type: includeType, studyYear: studyYear }),
    },
  },
  courses: {
    bsonType: "object",
    required: ["_id", "title", "semester"],
    properties: {
      _id: str, title: str, semester: { enum: ["fall", "spring"] }, ects: ects,
      language: strOrNull, description: strOrNull, objectives: strOrNull, examInfo: strOrNull, url: strOrNull,
      professors: ref("professorId", { name: str, role: role }),
      programs: ref("programId", { name: str, type: includeType, studyYear: studyYear }),
      prerequisites: ref("courseId", { title: str, source: { enum: ["scraped", "synthetic"] } }),
    },
  },
  professors: {
    bsonType: "object",
    required: ["_id", "firstName", "lastName"],
    properties: {
      _id: str, firstName: str, lastName: str, position: strOrNull, profileUrl: strOrNull,
      courses: ref("courseId", { title: str, role: role }),
    },
  },
  students: {
    bsonType: "object",
    required: ["_id", "firstName", "lastName", "email", "matriculationNumber", "startYear"],
    properties: {
      _id: str, firstName: str, lastName: str, email: str, matriculationNumber: str, startYear: intT,
      synthetic: { bsonType: "bool" },
      program: {
        bsonType: "object", required: ["programId"],
        properties: { programId: str, name: str, startYear: intT, status: { enum: ["active", "graduated"] } },
      },
      courses: ref("courseId", {
        title: str, ects: ects, semester: str, status: { enum: ["enrolled", "passed", "failed"] }, grade: grade,
      }),
    },
  },
};

const existing = usi.getCollectionNames();
for (const [name, schema] of Object.entries(schemas)) {
  const options = { validator: { $jsonSchema: schema }, validationLevel: "strict", validationAction: "error" };
  if (existing.includes(name)) {
    usi.runCommand(Object.assign({ collMod: name }, options));
  } else {
    usi.createCollection(name, options);
  }
}

usi.courses.createIndex({ semester: 1 });
usi.courses.createIndex({ "professors.professorId": 1 });
usi.courses.createIndex({ "programs.programId": 1 });
usi.courses.createIndex({ title: "text", description: "text", objectives: "text" }, { name: "course_text" });
usi.professors.createIndex({ lastName: 1 });
usi.students.createIndex({ "program.programId": 1 });
usi.students.createIndex({ "courses.courseId": 1 });
usi.students.createIndex({ matriculationNumber: 1 }, { unique: true });

print("usi collections ready: " + usi.getCollectionNames().sort().join(", "));
