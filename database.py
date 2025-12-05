import os
import json
from datetime import datetime
from sqlalchemy import create_engine, Column, Integer, String, Text, DateTime
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker

DATABASE_URL = os.environ.get("DATABASE_URL")

engine = create_engine(DATABASE_URL) if DATABASE_URL else None
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine) if engine else None
Base = declarative_base()


class Project(Base):
    __tablename__ = "projects"
    
    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(255), nullable=False)
    files = Column(Text, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


def init_db():
    if engine:
        Base.metadata.create_all(bind=engine)


def get_db():
    if SessionLocal:
        db = SessionLocal()
        try:
            return db
        except Exception:
            db.close()
            raise
    return None


def save_project(name: str, files: dict) -> int:
    db = get_db()
    if not db:
        return -1
    
    try:
        existing = db.query(Project).filter(Project.name == name).first()
        
        if existing:
            existing.files = json.dumps(files)
            existing.updated_at = datetime.utcnow()
            db.commit()
            return existing.id
        else:
            project = Project(
                name=name,
                files=json.dumps(files)
            )
            db.add(project)
            db.commit()
            db.refresh(project)
            return project.id
    except Exception as e:
        db.rollback()
        raise e
    finally:
        db.close()


def load_project(project_id: int) -> dict:
    db = get_db()
    if not db:
        return None
    
    try:
        project = db.query(Project).filter(Project.id == project_id).first()
        if project:
            return {
                "id": project.id,
                "name": project.name,
                "files": json.loads(project.files),
                "created_at": project.created_at,
                "updated_at": project.updated_at
            }
        return None
    finally:
        db.close()


def load_project_by_name(name: str) -> dict:
    db = get_db()
    if not db:
        return None
    
    try:
        project = db.query(Project).filter(Project.name == name).first()
        if project:
            return {
                "id": project.id,
                "name": project.name,
                "files": json.loads(project.files),
                "created_at": project.created_at,
                "updated_at": project.updated_at
            }
        return None
    finally:
        db.close()


def list_projects() -> list:
    db = get_db()
    if not db:
        return []
    
    try:
        projects = db.query(Project).order_by(Project.updated_at.desc()).all()
        return [{
            "id": p.id,
            "name": p.name,
            "created_at": p.created_at,
            "updated_at": p.updated_at
        } for p in projects]
    finally:
        db.close()


def delete_project(project_id: int) -> bool:
    db = get_db()
    if not db:
        return False
    
    try:
        project = db.query(Project).filter(Project.id == project_id).first()
        if project:
            db.delete(project)
            db.commit()
            return True
        return False
    except Exception:
        db.rollback()
        return False
    finally:
        db.close()


init_db()
